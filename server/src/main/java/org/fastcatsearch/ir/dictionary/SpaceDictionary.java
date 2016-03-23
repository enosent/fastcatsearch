package org.fastcatsearch.ir.dictionary;

import org.apache.lucene.store.InputStreamDataInput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.fastcatsearch.ir.io.CharVector;
import org.fastcatsearch.ir.io.DataInput;
import org.fastcatsearch.ir.io.DataOutput;
import org.fastcatsearch.ir.util.CharVectorHashSet;
import org.fastcatsearch.plugin.analysis.AnalysisPluginSetting.ColumnSetting;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class SpaceDictionary extends MapDictionary {

	private final static String DELIMITER = "\\s";
	private Set<CharVector> wordSet;

	public SpaceDictionary() {
		this(false);
	}

	public SpaceDictionary(boolean ignoreCase) {
		super(ignoreCase);
		if(wordSet == null) {
			wordSet = new CharVectorHashSet(ignoreCase);
		}
	}

	public SpaceDictionary(File file, boolean ignoreCase) {
		super(file, ignoreCase);
	}

	public SpaceDictionary(InputStream is, boolean ignoreCase) {
		super(is, ignoreCase);
	}

	public Set<CharVector> getWordSet() {
		return wordSet;
	}

	public void setWordSet(Set<CharVector> wordSet) {
		this.wordSet = wordSet;
	}

	public Set<CharVector> getUnmodifiableWordSet() {
		return Collections.unmodifiableSet(wordSet);
	}
	
	private static final Pattern ptn = Pattern.compile("^[\\x00-\\x7F]*$");

	@Override
	public void addEntry(String word, Object[] values, List<ColumnSetting> columnList) {
		String keyword = word.replaceAll(DELIMITER, "");
		wordSet.add(new CharVector(keyword));
		String[] list = word.split(DELIMITER);
		super.addEntry(keyword, list, columnList);
		for (int i = 0; i < list.length; i++) {
			String str = list[i].trim();
			//ASCII 골라내기
			if(!ptn.matcher(str).find()) {
				wordSet.add(new CharVector(list[i].trim()));
			}
		}
	}

	@Override
	public void writeTo(OutputStream out) throws IOException {
		super.writeTo(out);
		DataOutput output = new OutputStreamDataOutput(out);
		// write size of synonyms
		output.writeVInt(wordSet.size());

		// write synonyms
		Iterator<CharVector> synonymIter = wordSet.iterator();
		for (; synonymIter.hasNext();) {
			CharVector value = synonymIter.next();
			output.writeUString(value.array(), value.start(), value.length());
		}
	}

	@Override
	public void readFrom(InputStream in) throws IOException {
		super.readFrom(in);
		DataInput input = new InputStreamDataInput(in);
		wordSet = new CharVectorHashSet(ignoreCase);
		int size = input.readVInt();
		for (int entryInx = 0; entryInx < size; entryInx++) {
			wordSet.add(new CharVector(input.readUString()));
		}
	}

	@Override
	public void reload(Object object) throws IllegalArgumentException {
		if (object != null && object instanceof SpaceDictionary) {
			super.reload(object);
			SpaceDictionary spaceDictionary = (SpaceDictionary) object;
			this.wordSet = spaceDictionary.getWordSet();

		} else {
			throw new IllegalArgumentException("Reload dictionary argument error. argument = " + object);
		}
	}

	@Override
	public void clear() {
		super.clear();
		wordSet.clear();
	}
}
