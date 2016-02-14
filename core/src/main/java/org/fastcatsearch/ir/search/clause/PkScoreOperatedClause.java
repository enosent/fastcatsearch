package org.fastcatsearch.ir.search.clause;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.fastcatsearch.ir.field.FieldDataParseException;
import org.fastcatsearch.ir.io.BytesDataOutput;
import org.fastcatsearch.ir.query.RankInfo;
import org.fastcatsearch.ir.search.IntPair;
import org.fastcatsearch.ir.search.PkScore;
import org.fastcatsearch.ir.search.PkScoreList;
import org.fastcatsearch.ir.search.PrimaryKeyIndexesReader;
import org.fastcatsearch.ir.search.SearchIndexesReader;

public class PkScoreOperatedClause extends OperatedClause {
	
	private List<IntPair> docNoList;
	private String keyword;
	private int cursor;
	
	public PkScoreOperatedClause(String id) {
		super(id);
	}

	public PkScoreOperatedClause(String id, PkScoreList boostList, SearchIndexesReader newSearchIndexesReader) throws FieldDataParseException, IOException {
		super(id);
		keyword = boostList.getKeyword();
		docNoList = new ArrayList<IntPair>(boostList.size());
		PrimaryKeyIndexesReader r = newSearchIndexesReader.getPrimaryKeyIndexesReader();
		
		BytesDataOutput tempOutput = new BytesDataOutput();
		for(int i = 0; i < boostList.size(); i++) {
			PkScore pkScore = boostList.get(i);
			int docNo = r.getDocNo(pkScore.getPk(), tempOutput);
			if(docNo != -1) {
				docNoList.add(new IntPair(r.getDocNo(pkScore.getPk(), tempOutput), pkScore.getScore()));
				logger.debug("conv {} > {}", pkScore.getPk(), docNoList.get(docNoList.size() -1).getKey());
			}
		}
		//docNo 오름차순으로 정렬한다.
		Collections.sort(docNoList);
	}

	@Override
	protected void initClause(boolean explain) {
	}

	@Override
	protected boolean nextDoc(RankInfo rankInfo) {
		if(cursor < docNoList.size()){
			IntPair intPair = docNoList.get(cursor);
			rankInfo.init(intPair.getKey(), intPair.getValue(), 1);
			logger.debug("pk boost {} : {}", intPair.getKey(), intPair.getValue());
			if(isExplain()){
				rankInfo.explain(id, intPair.getValue(), keyword);
			}
			cursor++;
			return true;
		}else{
			rankInfo.init(-1, 0);
			return false;
		}
	}

	@Override
	public void close() {
		docNoList = null;
	}

	@Override
    public void printTrace(Writer writer, int indent, int depth) throws IOException {
        String indentSpace = "";
        if(depth > 0){
            for (int i = 0; i < (depth - 1) * indent; i++) {
                indentSpace += " ";
            }

            for (int i = (depth - 1) * indent, p = 0; i < depth * indent; i++, p++) {
                if(p == 0){
                    indentSpace += "|";
                }else{
                    indentSpace += "-";
                }
            }
        }
        writer.append(indentSpace).append("[PkScore] ").append(keyword).append(" [").append(String.valueOf(docNoList.size())).append("] ").append(id).append("\n");

    }

}
