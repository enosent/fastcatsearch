package org.fastcatsearch.ir.search.clause;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Created by swsong on 2016. 2. 14..
 */
public class TermOccurrenceScorer {

    private static Logger logger = LoggerFactory.getLogger(TermOccurrenceScorer.class);

    public static int calculateScore(List<TermOccurrences> termOccurrencesList, int totalTermSize){

        if(termOccurrencesList == null || termOccurrencesList.size() == 0 || totalTermSize <= 0) {
            return 0;
        }
        List<TermOccur> occurList = new ArrayList<TermOccur>();

        for(TermOccurrences termOccurs : termOccurrencesList) {
            String termString = termOccurs.getTermString();
            String synonymOf = termOccurs.getSynonymOf();
            int queryPosition =  termOccurs.getQueryPosition();
            int[] positions = termOccurs.getPosition();
            Term term = new Term(termString, synonymOf, queryPosition);
            for(int pos : positions) {
                occurList.add(new TermOccur(term, pos));
            }
        }

        Collections.sort(occurList);

        for(TermOccur o : occurList) {
            logger.debug("{}", o);
        }

        int size = occurList.size();
        for (int p = 0; p < size - 1; p++) {
            TermOccur pivot = occurList.get(p);
            for (int i = p + 1; i < size; i++) {
                TermOccur target = occurList.get(i);
                logger.debug("Compare {} >> {}", pivot, target);
            }
        }
        return -1;
    }

    static class Term {
        String term;
        String synonymOf;
        int queryPosition;

        public Term(String term, String synonymOf, int queryPosition) {
            this.term = term;
            this.synonymOf = synonymOf;
            this.queryPosition = queryPosition;
        }

        public String getTerm() {
            return term;
        }

        public String getSynonymOf() {
            return synonymOf;
        }

        public int getQueryPosition() {
            return queryPosition;
        }

        @Override
        public String toString() {
            return "term[" + term + "] synOf[" + synonymOf + "] queryPos[" + queryPosition + "]";
        }
    }

    static class TermOccur implements Comparable {
        Term term;
        int pos;

        public TermOccur(Term term, int pos) {
            this.term = term;
            this.pos = pos;
        }

        public Term getTerm() {
            return term;
        }

        public int getPos() {
            return pos;
        }

        @Override
        public int compareTo(Object o) {
            return pos - ((TermOccur) o).pos;
        }

        @Override
        public String toString() {
            return term + " pos[" + pos + "]";
        }
    }



}
