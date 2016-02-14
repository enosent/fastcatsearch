/*
 * Copyright 2013 Websquared, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fastcatsearch.ir.search.clause;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;

import org.fastcatsearch.ir.query.RankInfo;



public class NotOperatedClause extends OperatedClause {
	private OperatedClause clause1;
	private OperatedClause clause2;
	private boolean hasNext1 = true;
	private boolean hasNext2 = true;
	
	private RankInfo docInfo1;
	private RankInfo docInfo2;
	
	public NotOperatedClause(OperatedClause clause1, OperatedClause clause2) {
		super("NOT");
		this.clause1 = clause1;
		this.clause2 = clause2;
	}

	protected boolean nextDoc(RankInfo rankInfo) {
		if(!hasNext1)
			return false;
		
		int doc1 = docInfo1.docNo();
		int doc2 = docInfo2.docNo();
		int score1 = docInfo1.score();

		do{
			while(hasNext1 && hasNext2 && (doc1 == doc2)){
				hasNext1 = clause1.next(docInfo1);
				hasNext2 = clause2.next(docInfo2);
				doc1 = docInfo1.docNo();
				doc2 = docInfo2.docNo();
				score1 = docInfo1.score();
			}
			
			while(hasNext2 && (doc1 > doc2)){
				hasNext2 = clause2.next(docInfo2);
				doc2 = docInfo2.docNo();
			}
			
			//2012-02-03 문서리스트가 끝나, 번호가 -1이면 루프를 끝낸다.
			//2014-6-17 swsong: -1로 셋팅되지 않은 경우도 있으므로, hasNext1가 false이면 루프를 끝내도록 수정.
		} while(hasNext1 && hasNext2 && doc1 == doc2); 
		
		if(hasNext1){
			rankInfo.init(doc1, score1);
			rankInfo.explain(docInfo1);
			hasNext1 = clause1.next(docInfo1);
			return true;
		}else{
			rankInfo.init(-1, 0);
			return false;
		}
		
	}

	@Override
	public void close() {
		if(clause1 != null){
			clause1.close();
		}
		if(clause2 != null){
			clause2.close();
		}		
	}

	@Override
	protected void initClause(boolean explain) {
		docInfo1 = new RankInfo(explain);
		docInfo2 = new RankInfo(explain);
		
		clause1.init(explanation != null ? explanation.createSubExplanation() : null);
		clause2.init(explanation != null ? explanation.createSubExplanation() : null);
		
		hasNext1 = clause1.next(docInfo1);
		hasNext2 = clause2.next(docInfo2);		
	}

//	@Override
//	protected void initExplanation() {
//		if(clause1 != null) {
//			clause1.setExplanation(explanation.createSub1());
//		}
//		if(clause2 != null) {
//			clause2.setExplanation(explanation.createSub2());
//		}
//	}

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
        writer.append(indentSpace).append("[NOT]\n");
        if(clause1 != null){
            clause1.printTrace(writer, indent, depth + 1);
        }
        if(clause2 != null){
            clause2.printTrace(writer, indent, depth + 1);
        }
    }
}
