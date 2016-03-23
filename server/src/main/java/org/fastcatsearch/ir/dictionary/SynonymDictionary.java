package org.fastcatsearch.ir.dictionary;

import org.apache.lucene.store.InputStreamDataInput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.fastcatsearch.ir.io.CharVector;
import org.fastcatsearch.ir.io.DataInput;
import org.fastcatsearch.ir.io.DataOutput;
import org.fastcatsearch.ir.util.CharVectorHashSet;
import org.fastcatsearch.ir.util.CharVectorUtils;
import org.fastcatsearch.plugin.analysis.AnalysisPluginSetting.ColumnSetting;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class SynonymDictionary extends MapDictionary {

	private Set<CharVector> wordSet;
	
	public SynonymDictionary(){
		this(false);
	}
	public SynonymDictionary(boolean isIgnoreCase) {
		super(isIgnoreCase);
		if(wordSet == null) {
			wordSet = new CharVectorHashSet(isIgnoreCase);
		}
	}

	public SynonymDictionary(File file, boolean isIgnoreCase) {
		super(file, isIgnoreCase);
		if(wordSet == null) {
			wordSet = new CharVectorHashSet(isIgnoreCase);
		}
	}

	public SynonymDictionary(InputStream is, boolean isIgnoreCase) {
		super(is, isIgnoreCase);
		if(wordSet == null) {
			wordSet = new CharVectorHashSet(isIgnoreCase);
		}
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

    private CharVector[] duplicateCharList(CharVector[] arr){
        if(arr != null) {
            CharVector[] list = new CharVector[arr.length];
            System.arraycopy(arr, 0, list, 0, arr.length);
            return list;
        }
        return null;
    }
	// key가 null일수 있다. 양방향의 경우.
	@Override
	public void addEntry(String keyword, Object[] values, List<ColumnSetting> columnSettingList) {

		ArrayList<CharVector> list = new ArrayList<CharVector>(4);

		CharVector mainWord = null;
		if (keyword != null) {
			keyword = keyword.trim();
			if (keyword.length() > 0) {
				mainWord = new CharVector(keyword);
				wordSet.add(mainWord);
                if(mainWord.hasWhitespaces()) {
                    for(CharVector w : CharVectorUtils.splitByWhitespace(mainWord)) {
                        wordSet.add(w);
                    }
                }
			}
		}

		if (values == null || values.length == 0) {
			return;
		}
		// 0번째에 유사어들이 컴마 단위로 모두 입력되어 있으므로 [0]만 확인하면 된다.
		String valueString = values[0].toString();
		// 중복제거.
		String[] synonyms = valueString.split(",");
		dedupSynonym(synonyms);
		for (int k = 0; k < synonyms.length; k++) {
			String synonym = synonyms[k].trim();
			if (synonym.length() > 0) {
				CharVector word = new CharVector(synonym);
				list.add(word);
				wordSet.add(word);
                if(word.hasWhitespaces()) {
                    for(CharVector w : CharVectorUtils.splitByWhitespace(word)) {
                        wordSet.add(w);
                    }
                }
			}
		}

		if (mainWord == null) {
			// 양방향.
			for (int j = 0; j < list.size(); j++) {
				CharVector key = list.get(j);
				CharVector[] value = new CharVector[list.size() - 1];
				int idx = 0;
				for (int k = 0; k < list.size(); k++) {
					CharVector val = list.get(k);
					if (!key.equals(val)) {
						// 다른것만 value로 넣는다.
						value[idx++] = val;
					}
				}
				// 유사어사전 데이터에 대표단어와 동일한 단어가 여러개 있을경우, 최종리스트는 더 적어지게 되므로 전체 array
				// 길이를 줄여준다.
				if (idx < value.length) {
					value = Arrays.copyOf(value, idx);
				}

				if (value.length > 0) {
                    CharVector[] value2 = map.get(key);
					if (value2 != null) {
                        // 이전값과 머징.
                        value2 = duplicateCharList(value2);
                        value = mergeSynonyms(value2, value);
					}
					map.put(key, value);
                    //공백을 제거한 key도 하나더 만든다.
                    if(key.hasWhitespaces()) {
                        key = key.duplicate().removeWhitespaces();
                        value2 = map.get(key);
                        if (value2 != null) {
                            // 이전값과 머징.
                            value2 = duplicateCharList(value2);
                            value = mergeSynonyms(value2, value);
                        }
                        map.put(key, value);
                    }
					//logger.debug("유사어 양방향 {} >> {}", key, join(value));
				}
			}

		} else {
			// 단방향.
			CharVector[] value = new CharVector[list.size()];
			int idx = 0;
			for (int j = 0; j < value.length; j++) {
				CharVector word = list.get(j);
				if (!mainWord.equals(word)) {
					// 다른것만 value로 넣는다.
					value[idx++] = word;
				}
			}
			if (idx < value.length) {
				value = Arrays.copyOf(value, idx);
			}
			if (value.length > 0) {
				CharVector[] value2 = map.get(mainWord);
				if (value2 != null) {
					// 이전값과 머징.
                    value2 = duplicateCharList(value2);
					value = mergeSynonyms(value2, value);
				}

                //
                //입력시 키워드는 공백제거.
                //
                map.put(mainWord, value);
                //공백이 포함되어 있다면, 제거한 단어도 함께 넣어준다.
                if(mainWord.hasWhitespaces()) {
                    map.put(mainWord.duplicate().removeWhitespaces(), value);
                }
                //logger.debug("유사어 단방향 {} >> {}", mainWord, join(value));
			}
		}
	}

	// 중복제거한다. 중복이 발견되면 "" 로 치환한다.
	private void dedupSynonym(String[] list) {
		if (list == null || list.length < 2) {
			return;
		}
		for (int i = 0; i < list.length; i++) {
			for (int j = i + 1; j < list.length; j++) {
				if (list[j].length() != 0 && list[i].equals(list[j])) {
					list[j] = "";
				}
			}
		}
		return;
	}

	private CharVector[] mergeSynonyms(CharVector[] value2, CharVector[] value) {
		int removedCount = 0;
		for (int i = 0; i < value.length; i++) {
			for (int j = i + 1; j < value2.length; j++) {
				if (value2[j] != null && value[i].equals(value2[j])) {
					value2[j] = null;
					removedCount++;
				}
			}
		}
		
		int newSize = value2.length + value.length - removedCount;
		CharVector[] list = new CharVector[newSize];
		int i = 0;
		for(CharVector v : value2){
			if(v != null){
				list[i++] = v;
			}
		}
		for(CharVector v : value){
			if(v != null){
				list[i++] = v;
			}
		}
		
		return list;
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
		if(object != null && object instanceof SynonymDictionary){
			super.reload(object);
			SynonymDictionary synonymDictionary = (SynonymDictionary) object;
			this.wordSet = synonymDictionary.getWordSet();
			
		}else{
			throw new IllegalArgumentException("Reload dictionary argument error. argument = " + object);
		}
	}

	@Override
	public void clear() {
		super.clear();
		wordSet.clear();
	}
}
