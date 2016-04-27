package org.fastcatsearch.ir.search;

import java.io.File;
import java.io.IOException;

import org.fastcatsearch.ir.common.IRException;
import org.fastcatsearch.ir.io.BufferedFileInput;
import org.fastcatsearch.ir.io.DataRef;
import org.fastcatsearch.ir.io.IOUtil;
import org.fastcatsearch.ir.io.IndexInput;
import org.fastcatsearch.ir.io.StreamInputRef;
import org.fastcatsearch.ir.settings.FieldSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ReferenceableIndexReader implements ReferenceableReader {
	protected static Logger logger = LoggerFactory.getLogger(ReferenceableIndexReader.class);
	
	protected String indexId;
	protected IndexInput dataInput;
	protected IndexInput multiValueInput;
	protected DataRef dataRef;
	protected int dataSize;//색인된 한 문서의 필드데이터의  길이

	protected boolean isMultiValue;
	
	public ReferenceableIndexReader() {}
	
	public void init(String indexId, FieldSetting refFieldSetting, File dataFile, File multiValueFile, int dataSize) throws IOException, IRException{
		this.indexId = indexId;
		this.dataSize = dataSize;
		dataInput = new BufferedFileInput(dataFile);
    	
    	isMultiValue = refFieldSetting.isMultiValue();
    	if(isMultiValue){
    		multiValueInput = new BufferedFileInput(multiValueFile);
    		dataRef = new StreamInputRef(multiValueInput, dataSize);
    	}else{
    		dataRef = new DataRef(dataSize);
    	}
//    	logger.debug("index reader init {}, {}, {}", indexId, dataSize, isMultiValue);
	}
	
	@Override
    public DataRef getRef() throws IOException{
		return dataRef;
	}
	
	@Override
    public void read(int docNo) throws IOException{
		
		if(isMultiValue){
			//multi-value는 위치가 8byte씩 기록되어있다. 
			dataInput.seek(IOUtil.SIZE_OF_LONG * docNo);
			long ptr = dataInput.readLong();
			if(ptr != -1){
				multiValueInput.seek(ptr);
				int count = multiValueInput.readVInt();
//				logger.debug("{} ref index read pos {}, seek {}, count {}, {}", indexId, multiValueInput.position(), ptr, count, multiValueInput);
				dataRef.init(count);
			}else{
				dataRef.init(0);
			}
		}else{
			long pos = ((long) dataSize) * docNo;
            dataInput.seek(pos);
			//이미 input의 position을 움직여 놓았으므로 더이상 아무것도 하지 않는다.
			dataInput.readBytes(dataRef.bytesRef().bytes, 0, dataSize);
//			logger.debug("fill group data to {} as {}", dataRef, IOUtil.readInt(dataRef.bytesRef().bytes, 0));
			dataRef.init(1); //single value는 한개 읽음으로 표시.
		}
	}
	
	public abstract ReferenceableIndexReader clone();
	
	@Override
    public void close() throws IOException {
		dataInput.close();
		
		if(isMultiValue && multiValueInput != null){
			multiValueInput.close();
		}
	}
}
