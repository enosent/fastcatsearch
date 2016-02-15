package org.fastcatsearch.ir.search.clause;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by swsong on 2016. 2. 14..
 */
public class TermOccurrenceScorerTest {

    private static Logger logger = LoggerFactory.getLogger(TermOccurrenceScorerTest.class);

    @Test
    public void test1() {

        int adjDistance = 5;
        int query = 0;
        List<TermOccurrences> termOccurrencesList = new ArrayList<TermOccurrences>();
        termOccurrencesList.add(new TermOccurrences("A", null, query++).withPosition(new int[]{1,6,9}));
        termOccurrencesList.add(new TermOccurrences("B", null, query++).withPosition(new int[]{3,7,15,40,80}));
        termOccurrencesList.add(new TermOccurrences("C", null, query++).withPosition(new int[]{2,8,17,38,77}));
        int tokenSize = termOccurrencesList.size();
        int score = TermOccurrenceScorer.calculateScore(termOccurrencesList, tokenSize, adjDistance, 100);

        logger.info("list = {}", termOccurrencesList);
        logger.info("score = {}", score);

    }
}
