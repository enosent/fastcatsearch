package org.fastcatsearch.ir.search.clause;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by swsong on 2016. 2. 14..
 */
public class TermOccurrenceScorerTest {

    private static Logger logger = LoggerFactory.getLogger(TermOccurrenceScorerTest.class);

    @Test
    public void test1() {

        int tokenSize = 3;
        List<TermOccurrences> termOccurrencesList = null;
        int score = TermOccurrenceScorer.calculateScore(termOccurrencesList, 3);

        logger.info("list = {}", termOccurrencesList);
        logger.info("score = {}", score);

    }
}
