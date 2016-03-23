package org.fastcatsearch.ir.dictionary;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.lucene.store.InputStreamDataInput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.fastcatsearch.ir.io.CharVector;
import org.fastcatsearch.ir.io.DataInput;
import org.fastcatsearch.ir.io.DataOutput;
import org.fastcatsearch.ir.util.CharVectorHashSet;
import org.fastcatsearch.plugin.analysis.AnalysisPluginSetting.ColumnSetting;

public class SetDictionary extends SourceDictionary {
	
	private Set<CharVector> set;

	public SetDictionary() {
		this(false);
	}
	
	public SetDictionary(boolean ignoreCase) {
		super(ignoreCase);
		set = new CharVectorHashSet(ignoreCase);
	}

	public SetDictionary(CharVectorHashSet set, boolean ignoreCase) {
		super(ignoreCase);
		this.set = set;
	}

	public SetDictionary(File file, boolean ignoreCase) {
		super(ignoreCase);
		if(!file.exists()){
			set = new CharVectorHashSet(ignoreCase);
			logger.error("사전파일이 존재하지 않습니다. file={}", file.getAbsolutePath());
			return;
		}
		InputStream is;
		try {
			is = new FileInputStream(file);
			readFrom(is);
			is.close();
		} catch (IOException e) {
			logger.error("", e);
		}
	}

	public SetDictionary(InputStream is, boolean ignoreCase){
		super(ignoreCase);
		try {
			readFrom(is);
		} catch (IOException e) {
			logger.error("", e);
		}
	}
	
	@Override
	public void addEntry(String keyword, Object[] value, List<ColumnSetting> columnList) {
		keyword = keyword.trim();
		if (keyword.length() > 0) {
			CharVector cv = new CharVector(keyword).removeWhitespaces();
			set.add(cv);
		}
	}

	public Set<CharVector> getUnmodifiableSet() {
		return Collections.unmodifiableSet(set);
	}
	
	public Set<CharVector> set() {
		return set;
	}
	
	public void setSet(Set<CharVector> set) {
		this.set = set;
	}
	
	public boolean contains(CharVector key){
		return set.contains(key);
	}
	
	@Override
	public void writeTo(OutputStream out) throws IOException {
		
		DataOutput output = new OutputStreamDataOutput(out);
		Iterator<CharVector> valueIter = set.iterator();
		//write size of set
		
		output.writeInt(set.size());
		//write values
		for(;valueIter.hasNext();) {
			CharVector value = valueIter.next();
			output.writeString(value.toString());
		}
	}

	@Override
	public void readFrom(InputStream in) throws IOException {
		
		DataInput input = new InputStreamDataInput(in);
		set = new CharVectorHashSet(ignoreCase);
		int size = input.readInt();
		
		for(int entryInx=0;entryInx < size; entryInx++) {
			set.add(new CharVector(input.readString()));
		}
	}

	@Override
	public void addSourceLineEntry(String line) {
		addEntry(line, null, null);
	}

	@Override
	public void reload(Object object) throws IllegalArgumentException {
		if(object != null && object instanceof SetDictionary){
			SetDictionary setDictionary = (SetDictionary) object;
			this.set = setDictionary.set();
			
		}else{
			throw new IllegalArgumentException("Reload dictionary argument error. argument = " + object);
		}
	}

	@Override
	public void clear() {
		super.clear();
		set.clear();
	}
}
