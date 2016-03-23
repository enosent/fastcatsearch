package org.fastcatsearch.ir.dictionary;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.store.InputStreamDataInput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.fastcatsearch.ir.io.CharVector;
import org.fastcatsearch.ir.io.DataInput;
import org.fastcatsearch.ir.io.DataOutput;
import org.fastcatsearch.ir.util.CharVectorHashSet;
import org.fastcatsearch.plugin.analysis.AnalysisPluginSetting.ColumnSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomDictionary extends SourceDictionary {
	private static Logger logger = LoggerFactory.getLogger(MapDictionary.class);
	private Set<CharVector> wordSet;
	private Map<CharVector, Object[]> map;
	
	public CustomDictionary() {
		this(false);
	}
	public CustomDictionary(boolean ignoreCase) {
		super(ignoreCase);
		map = new HashMap<CharVector, Object[]>();
		wordSet = new CharVectorHashSet(ignoreCase);
	}
	public CustomDictionary(File file, boolean ignoreCase) {
		super(ignoreCase);
        wordSet = new CharVectorHashSet(ignoreCase);
		if (!file.exists()) {
			map = new HashMap<CharVector, Object[]>();
			logger.error("사전파일이 존재하지 않습니다. file={}", file.getAbsolutePath());
			return;
		}
		InputStream is = null;
		try {
			is = new FileInputStream(file);
			readFrom(is);
			is.close();
		} catch (IOException e) {
			logger.error("", e);
		}
		
	}
	
	public CustomDictionary(InputStream is, boolean ignoreCase) {
		super(ignoreCase);
		try {
			readFrom(is);
		} catch (IOException e) {
			logger.error("",e);
		}
	}
	
	public Set<CharVector> getWordSet() {
		return wordSet;
	}
	
	public Map<CharVector, Object[]> getUnmodifiableMap() {
		return Collections.unmodifiableMap(map);
	}

	public Map<CharVector, Object[]> map() {
		return map;
	}

	public void setMap(Map<CharVector, Object[]> map) {
		this.map = map;
	}
	
	@Override
	public void writeTo(OutputStream out) throws IOException {
		DataOutput output = (DataOutput) new OutputStreamDataOutput(out);
		Iterator<CharVector> keySet = map.keySet().iterator();
		// write size of map
		output.writeVInt(map.size());
		// write key and value map
		for (; keySet.hasNext();) {
			// write key
			CharVector key = keySet.next();
			output.writeUString(key.array(), key.start(), key.length());
			
			// write values
			Object[] values = map.get(key);
			output.writeVInt(values.length);
			for (Object value : values) {
				if(value instanceof CharVector) {
					output.writeByte(1);
					CharVector v = (CharVector) value;
					output.writeUString(v.array(), v.start(), v.length());
				} else if(value instanceof CharVector[]) {
					output.writeByte(2);
					CharVector[] list = (CharVector[]) value;
					output.writeVInt(list.length);
					for (CharVector v : list) {
						output.writeUString(v.array(), v.start(), v.length());
					}
				}
				
			}
		}
		output.writeVInt(wordSet.size());

		Iterator<CharVector> iterator = wordSet.iterator();
		while (iterator.hasNext()) {
			CharVector value = iterator.next();
			output.writeUString(value.array(), value.start(), value.length());
		}
	}

	@Override
	public void readFrom(InputStream in) throws IOException {
		DataInput input = new InputStreamDataInput(in);
		
		map = new HashMap<CharVector, Object[]>();
		int size = input.readVInt();
		for (int entryInx = 0; entryInx < size; entryInx++) {
			CharVector key = new CharVector(input.readUString());

			int valueLength = input.readVInt();

			Object[] values = new Object[valueLength];

			for (int valueInx = 0; valueInx < valueLength; valueInx++) {
				
				int type = input.readByte();
				if(type == 1 ) {
					values[valueInx] = new CharVector(input.readUString());	
				} else if(type == 2 ) {
					int len = input.readVInt();
					CharVector[] list = new CharVector[len];
					for (int j = 0; j < len; j++) {
						list[j] = new CharVector(input.readUString());
					}
				}
				
				
			}
			map.put(key, values);
		}
		
		wordSet = new CharVectorHashSet(ignoreCase);
		size = input.readVInt();
		for (int entryInx = 0; entryInx < size; entryInx++) {
			wordSet.add(new CharVector(input.readUString()));
		}
	}

	@Override
	public void addEntry(String keyword, Object[] values, List<ColumnSetting> columnSettingList) {
		if (keyword == null) {
			return;
		}
		keyword = keyword.trim();
		if(keyword.length() == 0) {
			return;
		}
		CharVector cv = new CharVector(keyword).removeWhitespaces();

		Object[] list = new Object[values.length];
		for (int i = 0; i < values.length; i++) {
			String value = values[i].toString();
			ColumnSetting columnSetting = columnSettingList.get(i);
			String separator = columnSetting.getSeparator();
			//separator가 존재하면 쪼개서 CharVector[] 로 넣고 아니면 그냥 CharVector 로 넣는다.
			if(separator != null && separator.length() > 0){
				String[] e = value.split(separator);
//				list[i] = new CharVector[e.length];
				CharVector[] el = new CharVector[e.length];
				for(int j = 0; j< e.length; j++){
					el[j] = new CharVector(e[j].trim());
					wordSet.add(el[j]);
				}
				list[i] = el;
			}else {
				CharVector val = new CharVector(value);
				list[i] = val;
				wordSet.add(val);
			}
		}
		
		map.put(cv, list);
	}
	
	
	@Override
	public void addSourceLineEntry(String line) {
		String[] kv = line.split("\t");
		if (kv.length == 1) {
			String value = kv[0].trim();
			addEntry(null, new Object[] { value }, null);
		} else if (kv.length == 2) {
			String keyword = kv[0].trim();
			String value = kv[1].trim();
			addEntry(keyword, new Object[] { value }, null);
		}
		
	}

	@Override
	public void reload(Object object) throws IllegalArgumentException {
		if(object != null && object instanceof CustomDictionary){
			CustomDictionary customDictionary = (CustomDictionary) object;
			this.map = customDictionary.map();
			
		}else{
			throw new IllegalArgumentException("Reload dictionary argument error. argument = " + object);
		}
	}

	@Override
	public void clear() {
		super.clear();
		wordSet.clear();
		map.clear();
	}
	
	public void setWordSet(Set<CharVector> wordSet) {
		this.wordSet = wordSet;
	}
}
