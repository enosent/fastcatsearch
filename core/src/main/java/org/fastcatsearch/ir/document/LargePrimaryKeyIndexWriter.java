package org.fastcatsearch.ir.document;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.fastcatsearch.ir.common.IndexFileNames;
import org.fastcatsearch.ir.document.merge.PrimaryKeyIndexMerger;
import org.fastcatsearch.ir.document.merge.PrimaryKeyIndexMultipleMerger;
import org.fastcatsearch.ir.io.BufferedFileInput;
import org.fastcatsearch.ir.io.BufferedFileOutput;
import org.fastcatsearch.ir.io.BytesBuffer;
import org.fastcatsearch.ir.io.IndexInput;
import org.fastcatsearch.ir.io.IndexOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 대용량 byte[] => int 를 색인하기 위해서 중간 flush가 가능하도록 한 writer. 중간 flush한 데이터도 중간중간 검색이 가능하도록 BytesToIntReader 로 유지하고 마지막 최종 머징때 하나로 합쳐서 BytesToIntReader 포맷으로 다시 만든다.
 * 
 * 입력되는 데이터는 먼저 memoryKeyIndex에 추가되고 일정 메모리사용량을 넘어서면 flush되어 BytesToIntReader로 유지된다. 중간검색시는 memoryKeyIndex에, BytesToIntReader[] 를 모두 검색한다.
 * */
public class LargePrimaryKeyIndexWriter implements BytesToIntWriter, BytesToIntReader {
	private static Logger logger = LoggerFactory.getLogger(LargePrimaryKeyIndexWriter.class);

	private MemoryPrimaryKeyIndex memoryKeyIndex;
	private List<PrimaryKeyIndexReader> readerList;
	File dir;
	private int flushCount;
	File tmpDir;
	String filename;
	int indexInterval;

	public LargePrimaryKeyIndexWriter(File dir, String filename, int indexInterval, int bucketSize) throws IOException {
		this.dir = dir;
		this.tmpDir = new File(dir, "pkTmp");
		if (tmpDir.exists()) {
			FileUtils.deleteQuietly(tmpDir);
		}
		memoryKeyIndex = new MemoryPrimaryKeyIndex(indexInterval, bucketSize);
		readerList = new ArrayList<PrimaryKeyIndexReader>();

		this.filename = filename;
		this.indexInterval = indexInterval;
	}

	public void flush() throws IOException {
		logger.debug("##flush #{}", flushCount);

		// 임시 파일 flushCount 로 구분.
		if (!tmpDir.exists()) {
			tmpDir.mkdirs();
		}

		File keyFile = getFlushFilename(flushCount);
		File indexFile = getFlushIndexFilename(flushCount);
		IndexOutput output = new BufferedFileOutput(keyFile);
		IndexOutput indexOutput = new BufferedFileOutput(indexFile);
		try {
			memoryKeyIndex.writeTo(output, indexOutput);
		} finally {
			output.close();
			indexOutput.close();
		}

		memoryKeyIndex.clear();

		logger.debug("Wrote data#{} file {}", flushCount, keyFile.getAbsolutePath());
		logger.debug("Wrote index#{} file {}", flushCount, indexFile.getAbsolutePath());

		IndexInput input = new BufferedFileInput(keyFile);
		IndexInput indexInput = new BufferedFileInput(indexFile);

		// flush 한 파일을 검색가능하도록 유지한다.
		readerList.add(new PrimaryKeyIndexReader(input, indexInput));

		flushCount++;
	}

	public void close() throws IOException {

		if (flushCount > 0 && memoryKeyIndex.count() > 0) {
			//flush가 존재하고, 메모리에 남아있는 것이 있으면 flush한다.
			if(memoryKeyIndex.count() > 0){
				flush();
			}
		}
		
		for (PrimaryKeyIndexReader reader : readerList) {
			reader.close();
		}
		
		IndexOutput output = new BufferedFileOutput(dir, filename);
		String indexFilename = IndexFileNames.getIndexFileName(filename);
		IndexOutput indexOutput = new BufferedFileOutput(dir, indexFilename);

		try {
			//memory index
			if (flushCount == 0) {
				// 바로 쓴다.
				memoryKeyIndex.writeTo(output, indexOutput);
				return;
			}

			// 최종머징.
			TempPrimaryKeyIndexReader[] reader = new TempPrimaryKeyIndexReader[flushCount];
			for (int i = 0; i < flushCount; i++) {
				File keyFile = getFlushFilename(i);
				reader[i] = new TempPrimaryKeyIndexReader(keyFile);
			}

			PrimaryKeyIndexMultipleMerger primaryKeyIndexMerger = new PrimaryKeyIndexMultipleMerger(reader);
			try {
				primaryKeyIndexMerger.mergeAndMakeIndex(output, indexOutput, indexInterval);
			} finally {
				if (primaryKeyIndexMerger != null) {
					primaryKeyIndexMerger.close();
				}
			}

			if (tmpDir.exists()) {
				FileUtils.deleteQuietly(tmpDir);
			}
		} finally {
			IOException exception = null;

			try {
				if (output != null) {
					output.close();
				}
			} catch (IOException e) {
				exception = e;
			}
			try {
				if (indexOutput != null) {
					indexOutput.close();
				}
			} catch (IOException e) {
				exception = e;
			}

			if (exception != null) {
				throw exception;
			}
		}

	}

	private File getFlushFilename(int id) {
		return new File(tmpDir, "key." + String.valueOf(id));
	}

	private File getFlushIndexFilename(int id) {
		return new File(tmpDir, "index." + String.valueOf(id));
	}

	@Override
	public int get(BytesBuffer buffer) throws IOException {

		return get(buffer.bytes, buffer.offset, buffer.length);
	}

	@Override
	public int get(byte[] data, int offset, int length) throws IOException {

		int value = memoryKeyIndex.get(data, offset, length);
		if (value == -1) {
			if (flushCount > 0) {
				for (int i = flushCount - 1; i >= 0; i--) {
					value = readerList.get(i).get(data, offset, length);
					if (value != -1) {
						return value;
					}
				}
			}
		}

		return -1;
	}

	@Override
	public int put(BytesBuffer buffer, int value) throws IOException {
		return put(buffer.bytes, buffer.offset, buffer.length, value);
	}

	@Override
	public int put(byte[] data, int offset, int length, int value) throws IOException {

		int oldValue = memoryKeyIndex.put(data, offset, length, value);
		if (oldValue == -1) {
			if (flushCount > 0) {
				for (int i = flushCount - 1; i >= 0; i--) {
					oldValue = readerList.get(i).get(data, offset, length);
					if (oldValue != -1) {
						logger.debug("found {} at #{}", oldValue, flushCount);
						break;
					}
				}
			}
		} else {
			logger.debug("found {} at DYNAMIC", oldValue);
		}
		return oldValue;
	}

	public long checkWorkingMemorySize() {
		return memoryKeyIndex.wokingMemorySize();
	}

}
