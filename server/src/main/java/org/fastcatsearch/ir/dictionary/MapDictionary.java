package org.fastcatsearch.ir.dictionary;

import org.apache.lucene.store.InputStreamDataInput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.fastcatsearch.ir.io.CharVector;
import org.fastcatsearch.ir.io.DataInput;
import org.fastcatsearch.ir.io.DataOutput;
import org.fastcatsearch.ir.util.CharVectorHashMap;
import org.fastcatsearch.plugin.analysis.AnalysisPluginSetting.ColumnSetting;

import java.io.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/*
 * map 범용 사전. 
 * CharVector : CharVector[] pair이다.
 * 만약 value에 Object[]를 사용하길 원한다면 custom dictionary를 사용한다.
 * 
 * 
 * */
public class MapDictionary extends SourceDictionary {

	protected Map<CharVector, CharVector[]> map;

	public MapDictionary() {
		this(false);
	}

	public MapDictionary(boolean ignoreCase) {
		super(ignoreCase);
		map = new CharVectorHashMap<CharVector[]>(ignoreCase);
	}

	public MapDictionary(CharVectorHashMap<CharVector[]> map) {
		super(map.isIgnoreCase());
		this.map = map;
	}

	public MapDictionary(File file, boolean ignoreCase) {
		super(ignoreCase);
		if (!file.exists()) {
			map = new CharVectorHashMap<CharVector[]>();
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

	public MapDictionary(InputStream is, boolean ignoreCase) {
		super(ignoreCase);
		try {
			readFrom(is);
		} catch (IOException e) {
			logger.error("", e);
		}
	}

	@Override
	public void clear() {
		super.clear();
		map.clear();
	}

	@Override
	public void addEntry(String keyword, Object[] values, List<ColumnSetting> columnList) {
        if (keyword == null) {
            return;
        }
        keyword = keyword.trim();
        if(keyword.length() == 0) {
            return;
        }

		CharVector[] list = new CharVector[values.length];
		for (int i = 0; i < values.length; i++) {
			String value = values[i].toString();
			list[i] = new CharVector(value);
		}
		
		CharVector cv = new CharVector(keyword).removeWhitespaces();
		map.put(cv, list);
	}

	public Map<CharVector, CharVector[]> getUnmodifiableMap() {
		return Collections.unmodifiableMap(map);
	}

	public Map<CharVector, CharVector[]> map() {
		return map;
	}
	
	public void setMap(Map<CharVector, CharVector[]> map) {
		this.map = map;
	}
	
	
	public boolean containsKey(CharVector key){
		return map.containsKey(key);
	}
	
	public CharVector[] get(CharVector key){
		return map.get(key);
	}

	@Override
	public void writeTo(OutputStream out) throws IOException {

		DataOutput output = new OutputStreamDataOutput(out);
		Iterator<CharVector> keySet = map.keySet().iterator();
		// write size of map
		output.writeVInt(map.size());
		// write key and value map
		for (; keySet.hasNext();) {
			// write key
			CharVector key = keySet.next();
			output.writeUString(key.array(), key.start(), key.length());
			// write values
			CharVector[] values = map.get(key);
			output.writeVInt(values.length);
			for (CharVector value : values) {
				output.writeUString(value.array(), value.start(), value.length());
			}
		}

	}

	@Override
	public void readFrom(InputStream in) throws IOException {
		DataInput input = new InputStreamDataInput(in);

		map = new CharVectorHashMap<CharVector[]>(ignoreCase);

		int size = input.readVInt();
		for (int entryInx = 0; entryInx < size; entryInx++) {
			CharVector key = new CharVector(input.readUString());

			int valueLength = input.readVInt();

			CharVector[] values = new CharVector[valueLength];

			for (int valueInx = 0; valueInx < valueLength; valueInx++) {
				values[valueInx] = new CharVector(input.readUString());
			}
			map.put(key, values);
		}

	}

	@Override
	public void addSourceLineEntry(String line) {
		String[] kv = line.split("\t");
		if (kv.length == 1) {
			String value = kv[0].trim();
			addEntry(null, new String[] { value }, null);
		} else if (kv.length == 2) {
			String keyword = kv[0].trim();
			String value = kv[1].trim();
			addEntry(keyword, new String[] { value }, null);
		}
	}

	@Override
	public void reload(Object object) throws IllegalArgumentException {
		if(object != null && object instanceof MapDictionary){
			MapDictionary mapDictionary = (MapDictionary) object;
			this.map = mapDictionary.map();
			
		}else{
			throw new IllegalArgumentException("Reload dictionary argument error. argument = " + object);
		}
	}
}