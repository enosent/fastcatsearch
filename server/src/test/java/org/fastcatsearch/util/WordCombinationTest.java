package org.fastcatsearch.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by swsong on 2015. 7. 29..
 */
public class WordCombinationTest {

    @Test
    public void testCombination() {

        List<String> candidates = new ArrayList<String>();

        candidates.add("A");
        candidates.add("b");
        candidates.add("C");
        candidates.add("d");

        List<WordCombination.WordEntry> result = WordCombination.getDescCombination(candidates);

        for(WordCombination.WordEntry r : result) {
            System.out.println(r);
        }
    }

    @Test
    public void testCombinationLong() {

        List<String> candidates = new ArrayList<String>();

        char a = 'a';
        for(int i = 0;i < 20; i++) {
            char c = (char)(a + i);
            candidates.add(String.valueOf(c));
            List<WordCombination.WordEntry> result = WordCombination.getDescCombination(candidates);
            System.out.println("candidates.size = " + candidates.size() + ", result.size = " + result.size());
            System.out.println("mem =" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/(1024*1024) + "MB");
        }

    }

}
