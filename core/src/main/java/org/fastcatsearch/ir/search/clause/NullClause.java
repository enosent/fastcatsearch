package org.fastcatsearch.ir.search.clause;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;

import org.fastcatsearch.ir.common.IRException;
import org.fastcatsearch.ir.query.HighlightInfo;
import org.fastcatsearch.ir.query.RankInfo;
import org.fastcatsearch.ir.search.SearchIndexesReader;

public class NullClause extends Clause {

    private static NullOperatedClause operatedClause;

    public NullClause() {
        super(null);
    }

    @Override
    public OperatedClause getOperatedClause(int docCount, SearchIndexesReader reader, HighlightInfo highlightInfo) throws ClauseException, IOException, IRException {
        return getOperatedClause();
    }

    public static OperatedClause getOperatedClause() {
        if (operatedClause == null) {
            operatedClause = new NullOperatedClause("");
        }

        return operatedClause;
    }
}

class NullOperatedClause extends OperatedClause {
    public NullOperatedClause(String collectionId) {
        super(collectionId);
    }

    @Override
    protected void initClause(boolean explain) {
    }

    @Override
    protected boolean nextDoc(RankInfo docInfo) {
        return false;
    }

    @Override
    public void close() {
    }

    @Override
    public void printTrace(Writer writer, int indent, int depth) throws IOException {

    }
}
