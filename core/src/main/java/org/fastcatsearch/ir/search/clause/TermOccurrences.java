package org.fastcatsearch.ir.search.clause;

/**
 * Created by swsong on 2016. 2. 14..
 */
public class TermOccurrences {
    private String termString;
    private String synonymOf;
    private int queryPosition;
    private int[] position;

    public TermOccurrences(String termString, String synonymOf, int queryPosition) {
        this.termString = termString;
        this.synonymOf = synonymOf;
        this.queryPosition = queryPosition;
    }

    public TermOccurrences withPosition(int[] position) {
        this.position = position;
        return this;
    }

    public String getTermString() {
        return termString;
    }

    public String getSynonymOf() {
        return synonymOf;
    }

    public int getQueryPosition() {
        return queryPosition;
    }

    public int[] getPosition() {
        return position;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("[").append(termString).append("] ");
        if (synonymOf != null) {
            b.append("Syn[").append(synonymOf).append("] ");
        }
        b.append("Query[").append(queryPosition).append("] Pos[");
        if(position != null) {
            for (int i = 0; i < position.length; i++) {
                if (i > 0) {
                    b.append(",");
                }
                b.append(position[i]);
            }
        }
        b.append("]");

        return b.toString();
    }
}
