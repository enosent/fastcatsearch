package org.fastcatsearch.transport;



import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.io.FileUtils;
import org.fastcatsearch.cluster.Node;
import org.fastcatsearch.common.BytesReference;
import org.fastcatsearch.common.ThreadPoolFactory;
import org.fastcatsearch.common.io.BlockingCachedStreamOutput;
import org.fastcatsearch.common.io.BytesStreamOutput;
import org.fastcatsearch.common.io.CachedStreamOutput;
import org.fastcatsearch.common.io.Streamable;
import org.fastcatsearch.control.JobExecutor;
import org.fastcatsearch.control.ResultFuture;
import org.fastcatsearch.env.Environment;
import org.fastcatsearch.job.Job;
import org.fastcatsearch.module.AbstractModule;
import org.fastcatsearch.settings.Settings;
import org.fastcatsearch.transport.common.ByteCounter;
import org.fastcatsearch.transport.common.FileChannelHandler;
import org.fastcatsearch.transport.common.FileTransportHandler;
import org.fastcatsearch.transport.common.MessageChannelHandler;
import org.fastcatsearch.transport.common.MessageCounter;
import org.fastcatsearch.transport.common.ReadableFrameDecoder;
import org.fastcatsearch.transport.common.SendFileResultFuture;
import org.fastcatsearch.transport.vo.StreamableThrowable;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.HashedWheelTimer;

public class TransportModule extends AbstractModule {
	
	private Map<Long, ResultFuture> resultFutureMap;

    private final AtomicLong requestIds = new AtomicLong();
	    
	private volatile ClientBootstrap clientBootstrap;

    private volatile ServerBootstrap serverBootstrap;
    
	private ConcurrentMap<Node, NodeChannels> connectedNodes;
	
	private volatile Channel serverChannel;
	
	private final Object[] connectMutex;
	private ExecutorService executorService;
	private int workerCount;
	private int bossCount;

	private int port;

	private boolean tcpNoDelay;

	private boolean tcpKeepAlive;

	private boolean reuseAddress;
	private int connectTimeout;

	private int tcpSendBufferSize;
	private int tcpReceiveBufferSize;
    
	private int sendFileChunkSize;
    private JobExecutor jobExecutor;
    
    private int cachedQueueSize;
    
    private final ReadWriteLock globalLock = new ReentrantReadWriteLock();
    private FileTransportHandler fileTransportHandler;
    private BlockingCachedStreamOutput fileStreamOutputCache;

	//색인 데이터 전송시 별도 대역폭의 네트워크를 생성하는지 여부.
	private boolean hasSeparateDataNetwork;

    public TransportModule(Environment environment, Settings settings, int port, JobExecutor jobExecutor){
        this(environment, settings, port, jobExecutor, false);
    }
	public TransportModule(Environment environment, Settings settings, int port, JobExecutor jobExecutor, boolean hasSeparateDataNetwork){
		super(environment, settings);
		this.port = port;
		this.jobExecutor = jobExecutor;
		this.connectMutex = new Object[500];
        for (int i = 0; i < connectMutex.length; i++) {
            connectMutex[i] = new Object();
        }
        this.hasSeparateDataNetwork = hasSeparateDataNetwork;
	}
	
	@Override
	public boolean doLoad(){
		
		this.workerCount = settings.getInt("worker_count", Runtime.getRuntime().availableProcessors() * 2);
        this.connectTimeout = settings.getInt("connect_timeout", 1000);
        this.bossCount = settings.getInt("boss_count", 1);
        this.tcpNoDelay = settings.getBoolean("tcp_no_delay", true);
        this.tcpKeepAlive = settings.getBoolean("tcp_keep_alive", true);
        this.reuseAddress = settings.getBoolean("reuse_address", true);
        this.tcpSendBufferSize = settings.getInt("tcp_send_buffer_size", 1048576);
        this.tcpReceiveBufferSize = settings.getInt("tcp_receive_buffer_size", 1048576);
        this.sendFileChunkSize = (int) settings.getByteSize("send_file_chunk_size", 3 * 1024 * 1024);
        this.cachedQueueSize = (int) settings.getInt("send_file_cache_queue_size", 10);
        logger.debug("Transport setting worker_count[{}], port[{}], connect_timeout[{}]",
                new Object[]{workerCount, port, connectTimeout});
        
		this.executorService = ThreadPoolFactory.newUnlimitedCachedDaemonThreadPool("transport-pool");
		/*
		 * Client
		 * */
		clientBootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(
                Executors.newCachedThreadPool(),
                bossCount,
                new NioWorkerPool(Executors.newCachedThreadPool(), workerCount),
                new HashedWheelTimer()
                ));
		
		clientBootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				ChannelHandler readableDecoder = new ReadableFrameDecoder();
				ByteCounter byteCounter = new ByteCounter("ClientByteCounter");
				MessageCounter messageCounter = new MessageCounter("ClientMessageCounter");
				return Channels.pipeline(byteCounter, 
						readableDecoder,
						messageCounter, 
						new MessageChannelHandler("ClientMessageChannelHandler", environment, TransportModule.this, jobExecutor));
			}
		});
		clientBootstrap.setOption("connectTimeoutMillis", connectTimeout);
        clientBootstrap.setOption("tcpNoDelay", tcpNoDelay);
        clientBootstrap.setOption("keepAlive", tcpKeepAlive);
        if (tcpSendBufferSize > 0) {
            clientBootstrap.setOption("sendBufferSize", tcpSendBufferSize);
        }
        if (tcpReceiveBufferSize > 0) {
            clientBootstrap.setOption("receiveBufferSize", tcpReceiveBufferSize);
        }
        clientBootstrap.setOption("reuseAddress", reuseAddress);
        
        /*
		 * Server
		 * */
        
        fileTransportHandler  = new FileTransportHandler(environment.filePaths()); 
        serverBootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool(),
                workerCount));
        
		serverBootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				ChannelHandler readableDecoder = new ReadableFrameDecoder();
				ByteCounter byteCounter = new ByteCounter("ServerByteCounter");
				MessageCounter messageCounter = new MessageCounter("ServerMessageCounter");
				return Channels.pipeline(byteCounter, 
						readableDecoder,
						messageCounter,
						new MessageChannelHandler("ServerMessageChannelHandler", environment, TransportModule.this, jobExecutor),
						new FileChannelHandler(TransportModule.this, fileTransportHandler)
						);
			}
		});
        serverBootstrap.setOption("child.tcpNoDelay", tcpNoDelay);
        serverBootstrap.setOption("child.keepAlive", tcpKeepAlive);
        if (tcpSendBufferSize > 0) {
            serverBootstrap.setOption("child.sendBufferSize", tcpSendBufferSize);
        }
        if (tcpReceiveBufferSize > 0) {
            serverBootstrap.setOption("child.receiveBufferSize", tcpReceiveBufferSize);
        }
        serverBootstrap.setOption("reuseAddress", reuseAddress);
        serverBootstrap.setOption("child.reuseAddress", reuseAddress);
        
        
        serverChannel = serverBootstrap.bind(new InetSocketAddress(port));
        logger.debug("Bound to port [{}]", port);
        
        connectedNodes = new ConcurrentHashMap<Node, NodeChannels>();
        resultFutureMap = new ConcurrentHashMap<Long, ResultFuture>();
        
        fileStreamOutputCache = new BlockingCachedStreamOutput(cachedQueueSize, sendFileChunkSize + 3 * 1024);
        return true;
	}
	
	@Override
	public boolean doUnload() {
        final CountDownLatch latch = new CountDownLatch(1);
        // make sure we run it on another thread than a possible IO handler thread
        execute(new Runnable() {
            @Override
            public void run() {
                globalLock.writeLock().lock();
                try {
                    for (Iterator<NodeChannels> it = connectedNodes.values().iterator(); it.hasNext(); ) {
                        NodeChannels nodeChannels = it.next();
                        it.remove();
                        nodeChannels.close();
                    }

                    if (serverChannel != null) {
                        try {
                            serverChannel.close().awaitUninterruptibly();
                        } finally {
                            serverChannel = null;
                        }
                    }

                    if (serverBootstrap != null) {
                        serverBootstrap.releaseExternalResources();
                        serverBootstrap = null;
                    }

                    if (clientBootstrap != null) {
                        clientBootstrap.releaseExternalResources();
                        clientBootstrap = null;
                    }
                    
                    fileStreamOutputCache.clear();
                } finally {
                    globalLock.writeLock().unlock();
                    latch.countDown();
                }
            }
        });

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // ignore
        }
        
        return true;
    }
	
	private Object connectLock(String nodeId) {
        int hash = nodeId.hashCode();
        // abs returns Integer.MIN_VALUE, so we need to protect against it...
        if (hash == Integer.MIN_VALUE) {
            hash = 0;
        }
        return connectMutex[Math.abs(hash) % connectMutex.length];
    }
	
	public void connectToNode(Node node) throws TransportException{
		
		logger.info("Connect to Node [{}]", node);
		
		globalLock.readLock().lock();
		
		try{
		
			synchronized (connectLock(node.id())) {
	           
	            try {
	                NodeChannels nodeChannels = connectedNodes.get(node);
	                if (nodeChannels != null) {
	                    return;
	                }
	
	                try {
	                    InetSocketAddress address = node.address();
						InetSocketAddress dataAddress = node.dataAddress();

						ChannelFuture connectHigh = clientBootstrap.connect(address);

						ChannelFuture connectLow = null;

						// 1. 내 노드가 데이터 전용 네트워크를 사용하고,
						// 2. 상대 노드도 데이터 전용 어드레스가 존재하면
						// 별도 네트워크를 생성한다.
						if(hasSeparateDataNetwork && dataAddress != null) {
							connectLow = clientBootstrap.connect(dataAddress);
						} else {
							//기존 대역폭이용.
							connectLow = clientBootstrap.connect(address);
						}

	                    nodeChannels = new NodeChannels();

		                try{
		                    connectLow.awaitUninterruptibly((long) (connectTimeout * 1.5));
		                    if (!connectLow.isSuccess()) {
		                        throw new TransportException(node, "connect_timeout[" + connectTimeout + "]", connectLow.getCause());
		                    }
		                    nodeChannels.setLowChannel(connectLow.getChannel());
		                    nodeChannels.getLowChannel().getCloseFuture().addListener(new ChannelCloseListener(node));
		                    logger.debug("##Internal Transport Low Channel {}", connectLow.getChannel());
		                    
		                    connectHigh.awaitUninterruptibly((long) (connectTimeout * 1.5));
		                    if (!connectHigh.isSuccess()) {
		                        throw new TransportException(node, "connect_timeout[" + connectTimeout + "]", connectHigh.getCause());
		                    }
                            logger.debug("##Internal Transport High Channel {}", connectHigh.getChannel());
		                    nodeChannels.setHighChannel(connectHigh.getChannel());
		                    nodeChannels.getHighChannel().getCloseFuture().addListener(new ChannelCloseListener(node));
		                } catch (RuntimeException e) {
		                    // clean the futures
		                    	connectLow.cancel();
		                    	connectHigh.cancel();
		                        if (connectLow.getChannel() != null && connectLow.getChannel().isOpen()) {
		                            try {
		                            	connectLow.getChannel().close();
		                            } catch (Exception e1) {
		                                // ignore
		                            }
		                        }
		                        if (connectHigh.getChannel() != null && connectHigh.getChannel().isOpen()) {
		                            try {
		                            	connectHigh.getChannel().close();
		                            } catch (Exception e1) {
		                                // ignore
		                            }
		                        }
		                    throw e;
		                }
	                    
	                    
	                } catch (Exception e) {
	                    nodeChannels.close();
	                    throw e;
	                }
	
	                NodeChannels existing = connectedNodes.putIfAbsent(node, nodeChannels);
	                if (existing != null) {
	                    // we are already connected to a node, close this ones
						existing.close();
	                } else {
	                    if (logger.isDebugEnabled()) {
	                        logger.debug("connected to node [{}]", node);
	                    }
	//                    transportServiceAdapter.raiseNodeConnected(node);
	                }
	
	            } catch (TransportException e) {
	                throw e;
	            } catch (Exception e) {
	                throw new TransportException(node, "General node connection failure", e);
	            }
	        }
		}finally{
			globalLock.readLock().unlock();
		}
	}
	
	private NodeChannels getNodeChannels(Node node) throws TransportException {
		if(!node.isEnabled()){
			throw new TransportException("node "+node.id() + " is disabled.");
		}
		NodeChannels channels = null;
		try {
			channels = connectedNodes.get(node);
			if (channels == null) {
				// 연결시도.
				connectToNode(node);
				channels = connectedNodes.get(node);
				// throw new TransportException(node, "연결할수 없습니다.");
			}
		} catch (TransportException e) {
			node.setInactive();
			throw e;
		}
		if(channels != null){
			if(!node.isActive()){
				node.setActive();
			}
		}
		return channels;
	}

    public ResultFuture sendRequest(final Node node, final Job job) throws TransportException {
    	if(node == null){
    		throw new TransportException("node is null");
    	}
//    	if(!node.isActive()){
//    		throw new TransportException("node is not active : "+node.toString());
//    	}
        final long requestId = newRequestId();
        try {
        	if (job.isNoResult()) {
        		sendMessageRequest(node, requestId, job);
        		return null;
        	}else{
	        	ResultFuture resultFuture = new ResultFuture(requestId, resultFutureMap);
	            resultFutureMap.put(requestId, resultFuture);
	            sendMessageRequest(node, requestId, job);
	            
	            return resultFuture;
        	}
        } catch (final Exception e) {
            resultFutureMap.remove(requestId);
           logger.error("", e);
           throw new TransportException("메시지 전송중 에러발생.", e);
        }
    }
    
    public SendFileResultFuture sendFile(final Node node, File sourcefile, File targetFile) throws TransportException {
    	if(node == null){
    		throw new TransportException("node is null");
    	}
//    	if(!node.isActive()){
//    		throw new TransportException("node is not active : "+node.toString());
//    	}
    	final long requestId = newRequestId();
    	try {
    		SendFileResultFuture resultFuture = new SendFileResultFuture(requestId, resultFutureMap);
            resultFutureMap.put(requestId, resultFuture);
            sendFileRequest(node, requestId, sourcefile, targetFile, resultFuture);
            
            return resultFuture;
        } catch (final Exception e) {
            resultFutureMap.remove(requestId);
           
           throw new TransportException("메시지 전송중 에러발생.", e);
        }
    }
    
    public void resultReceived(long requestId, Object result) {
    	ResultFuture resultFuture = resultFutureMap.remove(requestId);
    	if(resultFuture == null){
    		//입력할 결과객체가 없음.
    		logger.warn("입력할 결과객체가 없음. timeout으로 제거되었을수있습니다. requestId={}, result={}", requestId, result);
    	}else{
    		resultFuture.put(result, true);
    	}
	}

	public void exceptionReceived(long requestId, StreamableThrowable e) {
		ResultFuture resultFuture = resultFutureMap.remove(requestId);
    	if(resultFuture == null){
    		//입력할 결과객체가 없음.
    		logger.warn("입력할 결과객체가 없음. timeout으로 제거되었을수있습니다. requestId={}, Throwable={}", requestId, e.getThrowable());
    	}else{
    		resultFuture.put(e.getThrowable(), false);
    	}
		
	}
	
	
    private long newRequestId() {
        return requestIds.getAndIncrement();
    }
    
    private void sendMessageRequest(final Node node, long requestId, Job request) throws IOException, TransportException {
		NodeChannels channels = getNodeChannels(node);
		Channel targetChannel = channels.getHighChannel();
		byte type = 0;
		type = TransportOption.setTypeMessage(type);
        byte status = 0;
        status = TransportOption.setRequest(status);
        CachedStreamOutput.Entry cachedEntry = CachedStreamOutput.popEntry();
        BytesStreamOutput stream = cachedEntry.bytes();
        stream.skip(MessageProtocol.HEADER_SIZE);
        stream.writeString(request.getClass().getName());
        stream.writeBoolean(request.isNoResult());
        stream.writeBoolean(request.isScheduled());
//        logger.debug("write class {}", request.getClass().getName());
        if(request instanceof Streamable){
        	Streamable streamable = (Streamable) request;
        	streamable.writeTo(stream);
        }
        stream.close();
        
        
        ChannelBuffer buffer = stream.bytesReference().toChannelBuffer();
        MessageProtocol.writeHeader(buffer, type, requestId, status);

        ChannelFuture future = targetChannel.write(buffer);
        future.addListener(new CacheFutureListener(cachedEntry));
    }
	
    private String getHashedFilePath(String filePath){
        UUID uuid = UUID.nameUUIDFromBytes(filePath.getBytes());
        return Long.toHexString(uuid.getMostSignificantBits()) + Long.toHexString(uuid.getLeastSignificantBits());
    }
    
    /*
     * header + seq(4) + [filepath(string) + filesize(long) + checksumCRC32(long)]+ hashfilepath(string) + datalength(vint) + data 
     * */
	private void sendFileRequest(final Node node, final long requestId, File sourcefile, File targetFile, SendFileResultFuture resultFuture) throws IOException, TransportException {
		NodeChannels channels = getNodeChannels(node);
		Channel targetChannel = channels.getLowChannel();
		byte type = 0;
		type = TransportOption.setTypeFile(type);
		byte status = 0;
		logger.debug("sendFileRequest {} type={}, {} >> {}", targetChannel, type, sourcefile.getAbsolutePath(), targetFile.getPath());
        FileChunkEnumeration enumeration = null;
        try{
        	if(!sourcefile.exists()){
        		throw new IOException("파일을 찾을수 없습니다.file = " + sourcefile.getAbsolutePath());
        	}
        	enumeration = new FileChunkEnumeration(sourcefile, sendFileChunkSize);
	    	long checksumCRC32 = FileUtils.checksumCRC32(sourcefile);//checksum 생성은 시간이 조금 소요되는 작업. 3G => 10초.
	        long fileSize = sourcefile.length();
	        long writeSize = 0;
	        String sourceFilePath = sourcefile.getAbsolutePath();
	        String targetFilePath = targetFile.getPath(); //원래 path를 그대로 이용해서 상대경로전송이 가능하도록 한다.
	        String hashedFilePath = getHashedFilePath(sourceFilePath);
	        logger.debug("Send filesize ={}, crc={}, file={}", new Object[]{fileSize, checksumCRC32, sourceFilePath});	        
	        
	    	for(int seq = 0; enumeration.hasMoreElements(); seq++){
	    		if(resultFuture.isCanceled()){
	    			break;
	    		}
	    		
	    		BytesReference bytesRef = enumeration.nextElement();
//	    		logger.debug("write file seq ={}, length={}", seq, bytesRef.length());
	    		
	    		writeSize += bytesRef.length();
	    		
//	    		CachedStreamOutput.Entry cachedEntry = CachedStreamOutput.popEntry();
	    		BlockingCachedStreamOutput.Entry cachedEntry = fileStreamOutputCache.popEntry();
	            BytesStreamOutput stream = cachedEntry.bytes();
	            stream.skip(MessageProtocol.HEADER_SIZE);
	    		
	            //write seq ( 0,1,2,3,4....)
	            stream.writeInt(seq);
	            
	            if(seq == 0){
	            	//시작시에는 파일명과 총파일크기를 보낸다.
	                //write file path
	                stream.writeString(targetFilePath);
	                //write file size
	                stream.writeLong(fileSize);
	                stream.writeLong(checksumCRC32);
	            }
	            
	            stream.writeString(hashedFilePath);
	            
	            
	            //write file data
	            stream.writeVInt(bytesRef.length());
	            if(bytesRef.length() > 0){
	            	stream.write(bytesRef.array(), bytesRef.arrayOffset(), bytesRef.length());
	            }
	            
	            stream.close();
	            //TODO 만약 이 라인 이전에 에러발생시 cache가 리턴되지 않고 누락되는 잠재버그가 발생할수있다.
	            
	            ChannelBuffer buffer = stream.bytesReference().toChannelBuffer();
	            MessageProtocol.writeHeader(buffer, type, requestId, status);
	            //
	            //TEST buffer 검증.
	            //
	            int readerIndex = buffer.readerIndex();
	            readerIndex += 2;
	            assert type == buffer.getByte(readerIndex);
	            readerIndex += 1;
	            readerIndex += 4;
	            
	            assert requestId == buffer.getLong(readerIndex);
	            readerIndex += 8;
	            assert status == buffer.getByte(readerIndex);
	            readerIndex += 1;
	            assert seq == buffer.getInt(readerIndex);
	            readerIndex += 4;
	            
	            ChannelFuture future = targetChannel.write(buffer);
	            future.addListener(new BlockingCacheFutureListener(fileStreamOutputCache, cachedEntry));
	    	}
        
	    	
	    	if(resultFuture.isCanceled()){
    			logger.info("파일전송이 중단되었습니다. file={}", sourceFilePath);
    		}else{
		    	assert fileSize == writeSize: "파일사이즈가 다릅니다.";
		    	if(fileSize != writeSize){
		    		logger.error("파일사이즈가 다릅니다. expected={}, actual={}, file={}", new Object[]{fileSize, writeSize, sourceFilePath});
		    	}else{
		    		logger.info("File Write Done filesize={}, file={}", writeSize, sourceFilePath);
		    	}
    		}
        }catch(Throwable t){
        	logger.error("", t);
        	throw new IOException(t);
        }finally{
        	if(enumeration != null){
        		enumeration.close();
        	}
        }
	}
	
	public void disconnectFromNode(Node node) {
		logger.debug("disconnectFromNode > {}", node);
		synchronized (connectLock(node.id())) {
			NodeChannels nodeChannels = connectedNodes.remove(node);
			if (nodeChannels != null) {
				try {
					nodeChannels.close();
				} finally {
					logger.debug("disconnected from [{}]", node);
					node.setInactive();
				}
			}
		}
	}
	
	
	private class ChannelCloseListener implements ChannelFutureListener {

        private final Node node;

        private ChannelCloseListener(Node node) {
            this.node = node;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            disconnectFromNode(node);
        }
    }
	
	public static class CacheFutureListener implements ChannelFutureListener {

        private final CachedStreamOutput.Entry cachedEntry;

        public CacheFutureListener(CachedStreamOutput.Entry cachedEntry) {
            this.cachedEntry = cachedEntry;
        }

        @Override
        public void operationComplete(ChannelFuture channelFuture) throws Exception {
            CachedStreamOutput.pushEntry(cachedEntry);
        }
    }
	
	public static class BlockingCacheFutureListener implements ChannelFutureListener {

        private final BlockingCachedStreamOutput.Entry cachedEntry;
        private final BlockingCachedStreamOutput cache;

        public BlockingCacheFutureListener(BlockingCachedStreamOutput cache, BlockingCachedStreamOutput.Entry cachedEntry) {
        	this.cache = cache;
            this.cachedEntry = cachedEntry;
        }

        @Override
        public void operationComplete(ChannelFuture channelFuture) throws Exception {
        	cache.pushEntry(cachedEntry);
        }
    }

	public void execute(Runnable requestRunnable) {
		executorService.execute(requestRunnable);
	}

	
}
