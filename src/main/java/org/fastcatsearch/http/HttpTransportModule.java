package org.fastcatsearch.http;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.fastcatsearch.cluster.Node;
import org.fastcatsearch.control.ResultFuture;
import org.fastcatsearch.env.Environment;
import org.fastcatsearch.module.AbstractModule;
import org.fastcatsearch.module.ModuleException;
import org.fastcatsearch.settings.Settings;
import org.fastcatsearch.transport.NetworkExceptionHelper;
import org.fastcatsearch.transport.NodeChannels;
import org.fastcatsearch.transport.TransportModule;
import org.fastcatsearch.transport.common.ByteCounter;
import org.fastcatsearch.transport.common.FileChannelHandler;
import org.fastcatsearch.transport.common.MessageChannelHandler;
import org.fastcatsearch.transport.common.MessageCounter;
import org.fastcatsearch.transport.common.ReadableFrameDecoder;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.timeout.ReadTimeoutException;

public class HttpTransportModule extends AbstractModule {

	
	private final AtomicLong requestIds = new AtomicLong();
	
	private volatile Channel serverChannel;
	private volatile ServerBootstrap serverBootstrap;
	
	final int maxContentLength;
    final int maxInitialLineLength;
    final int maxHeaderSize;
    final int maxChunkSize;

    private final int workerCount;

//    private final boolean blockingServer;

    final boolean compression;

    private final int compressionLevel; //expected: 0-9, 0 : no compression

    final boolean resetCookies;

    private final int port;

    private final String bindHost;

    private final String publishHost;

    private final Boolean tcpNoDelay;

    private final Boolean tcpKeepAlive;

    private final Boolean reuseAddress;

    private final long tcpSendBufferSize;
    private final long tcpReceiveBufferSize;

    final int maxCumulationBufferCapacity; //Integer.MAX_VALUE가 최대이다. 넘는다면 재설정해준다.
    final int maxCompositeBufferComponents;
	
	private ExecutorService executorService;
	private int bossCount;

	private ConcurrentHashMap<Node, NodeChannels> connectedNodes;

	private ConcurrentHashMap<Long, ResultFuture> resultFutureMap;

	
	private volatile HttpServerAdapter httpServerAdapter;
	
	public HttpTransportModule(Environment environment, Settings settings) {
		super(environment, settings);
		
		maxContentLength = settings.getInt("max_content_length", settings.getInt("http.max_content_length", 100 * 1024 * 1024));
        this.maxChunkSize = settings.getInt("max_chunk_size", settings.getInt("http.max_chunk_size", 8 * 1024));
        this.maxHeaderSize = settings.getInt("max_header_size", settings.getInt("http.max_header_size", 8 * 1024));
        this.maxInitialLineLength = settings.getInt("max_initial_line_length", settings.getInt("http.max_initial_line_length", 4 * 1024));
        // don't reset cookies by default, since I don't think we really need to
        // note, parsing cookies was fixed in netty 3.5.1 regarding stack allocation, but still, currently, we don't need cookies
        this.resetCookies = settings.getBoolean("reset_cookies", settings.getBoolean("http.reset_cookies", false));
        this.maxCumulationBufferCapacity = settings.getInt("max_cumulation_buffer_capacity", 0);
        this.maxCompositeBufferComponents = settings.getInt("max_composite_buffer_components", -1);
        this.workerCount = settings.getInt("worker_count", Runtime.getRuntime().availableProcessors() * 2);
        this.port = settings.getInt("http.port");
        this.bindHost = settings.getString("bind_host", settings.getString("http.bind_host", settings.getString("http.host")));
        this.publishHost = settings.getString("publish_host", settings.getString("http.publish_host", settings.getString("http.host")));
        this.tcpNoDelay = settings.getBoolean("tcp_no_delay", true);
        this.tcpKeepAlive = settings.getBoolean("tcp_keep_alive", true);
        this.reuseAddress = settings.getBoolean("reuse_address", true);
        this.tcpSendBufferSize = settings.getInt("tcp_send_buffer_size");
        this.tcpReceiveBufferSize = settings.getInt("tcp_receive_buffer_size");
        this.compression = settings.getBoolean("http.compression", false);
        this.compressionLevel = settings.getInt("http.compression_level", 6);
	}
	
	
	@Override
	protected boolean doLoad() throws ModuleException {
		
		serverBootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool(),
                workerCount));
        
		serverBootstrap.setPipelineFactory(new MyChannelPipelineFactory(this));
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
		
		
		return true;
	}

	@Override
	protected boolean doUnload() throws ModuleException {
		return false;
	}
	
	
	static class MyChannelPipelineFactory implements ChannelPipelineFactory {


        private final HttpRequestHandler requestHandler;
        private final HttpTransportModule transport;
        
        MyChannelPipelineFactory(HttpTransportModule transport) {
            this.transport = transport;
            this.requestHandler = new HttpRequestHandler(transport);
        }

        @Override
        public ChannelPipeline getPipeline() throws Exception {
            ChannelPipeline pipeline = Channels.pipeline();
//            pipeline.addLast("openChannels", transport.serverOpenChannels);
            HttpRequestDecoder requestDecoder = new HttpRequestDecoder(
                    transport.maxInitialLineLength,
                    transport.maxHeaderSize,
                    transport.maxChunkSize
            );
            if (transport.maxCumulationBufferCapacity > 0) {
//                if (transport.maxCumulationBufferCapacity > Integer.MAX_VALUE) {
//                    requestDecoder.setMaxCumulationBufferCapacity(Integer.MAX_VALUE);
//                } else {
                    requestDecoder.setMaxCumulationBufferCapacity((int) transport.maxCumulationBufferCapacity);
//                }
            }
            if (transport.maxCompositeBufferComponents != -1) {
                requestDecoder.setMaxCumulationBufferComponents(transport.maxCompositeBufferComponents);
            }
            pipeline.addLast("decoder", requestDecoder);
            if (transport.compression) {
                pipeline.addLast("decoder_compress", new HttpContentDecompressor());
            }
            HttpChunkAggregator httpChunkAggregator = new HttpChunkAggregator(transport.maxContentLength);
            if (transport.maxCompositeBufferComponents != -1) {
                httpChunkAggregator.setMaxCumulationBufferComponents(transport.maxCompositeBufferComponents);
            }
            pipeline.addLast("aggregator", httpChunkAggregator);
            pipeline.addLast("encoder", new HttpResponseEncoder());
            if (transport.compression) {
                pipeline.addLast("encoder_compress", new HttpContentCompressor(transport.compressionLevel));
            }
            pipeline.addLast("handler", requestHandler);
            return pipeline;
        }
    }

	public void httpServerAdapter(HttpServerAdapter httpServerAdapter) {
        this.httpServerAdapter = httpServerAdapter;
    }
	
	public void dispatchRequest(HttpRequest request, HttpChannel httpChannel) {
		httpServerAdapter.dispatchRequest(request, httpChannel);
	}


	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
		if (e.getCause() instanceof ReadTimeoutException) {
            if (logger.isTraceEnabled()) {
                logger.trace("Connection timeout [{}]", ctx.getChannel().getRemoteAddress());
            }
            ctx.getChannel().close();
        } else {
            if (!isLoaded) {
                // ignore
                return;
            }
            if (!NetworkExceptionHelper.isCloseConnectionException(e.getCause())) {
                logger.warn("Caught exception while handling client http traffic, closing connection {}", e.getCause(), ctx.getChannel());
                ctx.getChannel().close();
            } else {
                logger.debug("Caught exception while handling client http traffic, closing connection {}", e.getCause(), ctx.getChannel());
                ctx.getChannel().close();
            }
        }
	}

}