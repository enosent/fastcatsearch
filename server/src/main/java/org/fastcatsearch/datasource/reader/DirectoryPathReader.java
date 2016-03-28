package org.fastcatsearch.datasource.reader;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.fastcatsearch.datasource.SourceModifier;
import org.fastcatsearch.datasource.reader.annotation.SourceReader;
import org.fastcatsearch.ir.common.IRException;
import org.fastcatsearch.ir.config.SingleSourceConfig;

@SourceReader(name="DIRECTORY_PATH")
@Deprecated
public class DirectoryPathReader extends SingleSourceReader<Map<String,Object>> implements FileFilter, Runnable {
	
	public DirectoryPathReader() {
		super();
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public DirectoryPathReader(String collectionId, File filePath, SingleSourceConfig singleSourceConfig, SourceModifier sourceModifier, String lastIndexTime)
			throws IRException {
		super(collectionId, filePath, singleSourceConfig, sourceModifier, lastIndexTime);
	}
	
	String rootPath;
	//String encoding;
	String fieldId;
	Pattern[] skipPatterns;
	Pattern[] acceptPatterns;
	List<String> filePaths;
	boolean finished;
	int bufferSize;
	int maxDepth;
	int maxCount;
	int currentDepth;
	
	private Map<String, Object> record;

	@Override
	public void init() throws IRException { 
		rootPath = getConfigString("rootPath","/");
		//encoding = getConfigString("encoding",null);
		fieldId = getConfigString("fieldId").trim().toUpperCase();
		String[] skipPatternStr = getConfigString("skipPatterns", "").trim().split("\n");
		String[] acceptPatternStr = getConfigString("acceptPatterns", "").trim().split("\n");
		maxDepth = getConfigInt("maxDepth");
		maxCount = getConfigInt("maxCount");
		bufferSize = getConfigInt("bufferSize");
		
		if(maxDepth < 0) {
			maxDepth = 0;
		}
		if(maxCount < 0) {
			maxCount = 0;
		}
		if(bufferSize < 100) {
			bufferSize = 100;
		}
		
		if (skipPatternStr != null) {
			skipPatterns = new Pattern[skipPatternStr.length];
			for (int inx = 0; inx < skipPatternStr.length; inx++) {
				if (skipPatternStr[inx] != null
						&& !"".equals(skipPatternStr[inx])) {
					skipPatterns[inx] = Pattern.compile(skipPatternStr[inx]);
				}
			}
		}
		
		if (acceptPatternStr != null) {
			acceptPatterns = new Pattern[acceptPatternStr.length];
			for (int inx = 0; inx < acceptPatternStr.length; inx++) {
				if (acceptPatternStr[inx] != null
						&& !"".equals(acceptPatternStr[inx])) {
					acceptPatterns[inx] = Pattern.compile(acceptPatternStr[inx]);
				}
			}
		}
		
		finished = false;
		filePaths = new ArrayList<String>();
		currentDepth = 0;
		Thread t = new Thread(this);
		t.start();
	}
	
	@Override
	protected void initParameters() { 
		registerParameter(new SourceReaderParameter("rootPath", "Data Root Path", "Root Filepath for Indexing. (Absolute Path)"
				, SourceReaderParameter.TYPE_STRING_LONG, true, null));
		registerParameter(new SourceReaderParameter("fieldId", "Mapping Field Id", "Mapping Field-Id In Collection Schema"
				, SourceReaderParameter.TYPE_STRING, true, "path"));
		//registerParameter(new SourceReaderParameter("encoding", "Encoding", "File encoding"
		//		, SourceReaderParameter.TYPE_STRING, false, null));
		registerParameter(new SourceReaderParameter("maxDepth", "Max Depth", "Maximum Depth For File Exploring"
				, SourceReaderParameter.TYPE_NUMBER, false, "10"));
		registerParameter(new SourceReaderParameter("skipPatterns", "Skip Patterns", "Skip Patterns (in regex)"
				, SourceReaderParameter.TYPE_TEXT, false, ""));
		registerParameter(new SourceReaderParameter("acceptPatterns", "Accept Only Patterns", "Describe Accept Only Patterns ( blank = accept all ) "
				, SourceReaderParameter.TYPE_TEXT, false, ""));
		registerParameter(new SourceReaderParameter("bufferSize", "Buffer Size", "Reading Buffer Size"
				, SourceReaderParameter.TYPE_NUMBER, false, "100"));
		registerParameter(new SourceReaderParameter("maxCount", "Max Count", "Document limit for indexing. ( 0 = no limit )"
				, SourceReaderParameter.TYPE_NUMBER, false, "0"));
	}

	@Override
	public boolean hasNext() throws IRException {
		if(record == null) {
			record = fill();
		}
		return record !=null;
	}

	@Override
	protected Map<String, Object> next() throws IRException {
		Map<String,Object> ret = record;
		record = null;
		if(ret != null) {
			return ret;
		} else {
			ret = fill();
		}
		return ret;
	}
	
	private Map<String, Object> fill() throws IRException {
		while (true) {
			if (filePaths.size() > 0) {
				logger.trace("fetch record..");
				Map<String, Object> record = new HashMap<String, Object>();
				String path = filePaths.remove(0);
				record.put(fieldId, path);
				return record;
			} else {
				try {
					logger.trace("waiting..");
					Thread.sleep(100);
				} catch (InterruptedException ex) { 
					logger.debug("CATCH INTERRUPT! {}", ex.getMessage());
				}
				if(finished && filePaths.size() == 0) {
					break;
				}
				continue;
			}
		}
		return null;
	}
	
	@Override
	public boolean accept(File file) {
		logger.trace("finished:{} / file:{}", finished, file);
		int currentDepth = this.currentDepth;
		while(!finished) {
			if(filePaths.size() > bufferSize) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException ignore) { }
				continue;
			}
			
			if(file.isDirectory()) {
				logger.trace("dir current:{} / max:{}", currentDepth, maxDepth);
				if (currentDepth + 1 <= maxDepth) {
					//싱글스레드 이기 때문에 가능.
					this.currentDepth++;
					file.listFiles(this);
					this.currentDepth = currentDepth;
				} else {
					logger.trace("Max Depth Over In Exploring..{}", file);
				}
			} else if(file.isFile()) {
				logger.trace("file : {}", file);
				String path = file.getAbsolutePath();
				
				if (skipPatterns != null && skipPatterns.length > 0) {
					for (int inx = 0; inx < skipPatterns.length; inx++) {
						try {
							if (skipPatterns[inx] != null && !"".equals(skipPatterns[inx])) {
								if(skipPatterns[inx].matcher(path).find()) {
									logger.trace("Skip Pattern Found In : {}", path);
									return false;
								}
							}
						} catch (IllegalArgumentException ignore) { }
					}
				}
				
				if(acceptPatterns != null && acceptPatterns.length > 0) {
					for (int inx = 0; inx < acceptPatterns.length; inx++) {
						try {
							if (acceptPatterns[inx] != null && !"".equals(acceptPatterns[inx])) {
								if(!acceptPatterns[inx].matcher(path).find()) {
									logger.trace("Not Accepted Pattern Found In : {}", path);
									return false;
								}
							}
						} catch (IllegalArgumentException ignore) { }
					}
				}
				filePaths.add(path);
			}
			break;
		}
		return false;
	}
	
	@Override
	public void run() {
		File rootFile = new File(rootPath);
		rootFile.listFiles(this);
		finished = true;
	}
}
