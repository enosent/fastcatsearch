package org.fastcatsearch.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by swsong on 2015. 7. 29..
 */
public class WordCombination {

    /**
     * 긴 조합부터 후보를 뽑아낸다. 단어 순서는 지켜준다. 단어순서가 없는 permutation은 연산비용이 너무 많이 들게 되므로 지원하지 않음.
     * */
    public static List<WordEntry> getDescCombination(List<String> candidates) {
        return getDescCombination(candidates, null, 4, 10);
    }
    public static List<WordEntry> getDescCombination(List<String> candidates, String delimiter, int maxCombinationSize, int maxCandidateSize) {
        List<WordEntry> result = new ArrayList<WordEntry>();

        /*
        * candidates 길이가 10을 넘을 경우 그 이상의 Term들은 제거한다.
        * */
        for (int i = candidates.size() - 1; i >= maxCandidateSize; i--) {
            candidates.remove(i);
        }

        int SIZE = Math.min(candidates.size(), maxCombinationSize);

        for(int m = SIZE; m > 0; m--) {
            //기준위치를 앞에서 부터 한칸씩 이동시키며 후보를 찾는다.
            for(int p = 0; p < SIZE; p++) {
                getCombination(null, candidates, p, m, result, delimiter);
            }
        }
        return result;
    }

    private static void getCombination(WordEntry word, List<String> candidates, int pos, int m, List<WordEntry> result, String delimiter) {
        if(word != null) {
            word.append(candidates.get(pos));
        } else {
            word = new WordEntry(candidates.get(pos), delimiter);
        }
        pos++;
        m--;
        if(m > 0) {
            for(int p = pos; p + m - 1 < candidates.size(); p++ ){
                //다음 pos부터 n-1개를 재귀적으로 뽑는다.
                if( pos + m <= candidates.size() ) {
                    getCombination(word.deepCopy(), candidates, p, m, result, delimiter);
                }
            }
            return;
        }
        result.add(word);
    }

    public static class WordEntry {
        private String delimiter;
        private StringBuffer sb;
        private List<String> elements;

        public WordEntry() {
            this(null);
        }

        public WordEntry(String delimiter) {
            this.delimiter = delimiter;
            sb = new StringBuffer();
        }

        public WordEntry(String word, String delimiter) {
            this.delimiter = delimiter;
            sb = new StringBuffer();
            append(word);
        }

        @Override
        public boolean equals(Object o) {
            return sb.toString().equals(o.toString());
        }

        public void append(String word){
            if(delimiter != null) {
                if (sb.length() > 0) {
                    sb.append(delimiter);
                }
            }
            sb.append(word);
            if(elements == null) {
                elements = new ArrayList<String>();
            }

            elements.add(word);
        }

        public String getWord() {
            return sb.toString();
        }

        public List<String> getElements() {
            return elements;
        }

        @Override
        public String toString() {
            return sb.toString();
        }

        public WordEntry deepCopy() {

            WordEntry newEntry = new WordEntry();
            newEntry.delimiter = delimiter;
            newEntry.sb = new StringBuffer(sb.toString());

            List<String> newElements = null;
            if(elements != null) {
                newElements = new ArrayList<String>();
                for(String s : elements) {
                    newElements.add(s);
                }
            }
            newEntry.elements = newElements;
            return newEntry;
        }
    }

    public static class WordEntryPair {
        private WordEntry entry;
        private String value;

        public WordEntryPair(WordEntry entry, String value) {
            this.entry = entry;
            this.value = value;
        }

        public WordEntry getEntry() {
            return entry;
        }

        public String getValue() {
            return value;
        }
    }
}
