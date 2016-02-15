package org.fastcatsearch.ir.search.clause;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by swsong on 2016. 2. 14..
 */
public class TermOccurrenceScorer {

    private static Logger logger = LoggerFactory.getLogger(TermOccurrenceScorer.class);

    public static int calculateScore(List<TermOccurrences> termOccurrencesList, int totalTermSize, int adjDistance, int weight){

        if(termOccurrencesList == null || termOccurrencesList.size() == 0 || totalTermSize <= 0) {
            return 0;
        }

        int[] scoreMatrix = new int[totalTermSize * totalTermSize];

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
        PIVOT_LOOP:
        for (int p = 0; p < size - 1; p++) {
            TermOccur pivot = occurList.get(p);
            int pPos = pivot.getPos();
            int typeCount = 0; //몇가지 텀이 존재하는지..
            for (int i = p + 1; i < size; i++) {
                TermOccur target = occurList.get(i);
                int tPos = target.getPos();
                int distance = tPos - pPos;

                if(distance > adjDistance) {
                    continue PIVOT_LOOP;
                }

                /**
                 * 1. adjdistance 안에 2개의 텀이 있다면 점수를 5-d+1로 적용한다.
                 */

                int score = adjDistance - distance + 1;
                // 이미 두 쌍이 계산되어 있는지 확인한다.
                int m = pivot.getTerm().getQueryPosition();
                int n = target.getTerm().getQueryPosition();
                int prevScore = scoreMatrix[m * totalTermSize + n];
                if(score > prevScore) {
                    scoreMatrix[m * totalTermSize + n] = score;
                }

                /*
                 * 2. 5이내에 2개존재하면 최대 5점. 순서가 틀리면 -1점
                 */

                /*
                 * 3. 2이내에 2개 모두 존재하면(붙어있을경우) 5점.
                 */


                /// 추가로 텀이 3개 이상일 경우는 typeCount
                /*
                 * 4. N(텀갯수) + 2이내에 3개가 존재하면 5점
                 * 5. N *1.5 이내에 N개가 모두 존재하면 5점.
                 * 6. N 이내에 N개가 모두 존재하면 10점.
                 */


                /*
                 * 7.더 추가적으로 필드길이가 작으면, 5점 추가.근데 어떻게 알아내지
                 */

                logger.debug("Distance[{}] Score[{}] Compare {} >> {}", distance, score, pivot, target);
            }
        }

        int totalScore = 0;
        for(int s : scoreMatrix) {
            totalScore += s;
        }

        logger.debug("scoreMatrix {}", scoreMatrix);
        return totalScore * weight;
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

    static class TermPair {

        String source;
        String target;

        public TermPair(String source, String target) {
            this.source = source;
            this.target = target;
        }

        public String getSource() {
            return source;
        }

        public String getTarget() {
            return target;
        }

    }



}
