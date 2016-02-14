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

/**
 * 왼쪽 op에서 일치한 단어(matchFlag기반) 가 우측 op에서 출현시는 문서를 포함하지 않는다.
 * 우측 op가 우선적인 선택적 OR이다.
 * */
public class LOrOperatedClause extends OperatedClause {
	private OperatedClause clause1;
	private OperatedClause clause2;
	private boolean hasNext1 = true;
	private boolean hasNext2 = true;
	
	private RankInfo docInfo1;
	private RankInfo docInfo2;
	
	public LOrOperatedClause(OperatedClause clause1, OperatedClause clause2) {
		super("LOR");
		
		if(clause1 == null) {
			clause1 = NullClause.getOperatedClause();
		}
		
		if(clause2 == null) {
			clause2 = NullClause.getOperatedClause();
		}
		
		this.clause1 = clause1;
		this.clause2 = clause2;
	}
	
	protected boolean nextDoc(RankInfo rankInfo) {
		if(hasNext1 || hasNext2){
			int doc1 = docInfo1.docNo();
			int doc2 = docInfo2.docNo();
			if(hasNext1 && hasNext2){
				if(doc1 < doc2){
					rankInfo.init(doc1, docInfo1.score(), docInfo1.hit());
					rankInfo.addMatchFlag(docInfo1.matchFlag());
					rankInfo.explain(docInfo1);
					hasNext1 = clause1.next(docInfo1);
				}else if(doc1 > doc2){
					rankInfo.init(doc2, docInfo2.score(), docInfo2.hit());
					rankInfo.addMatchFlag(docInfo2.matchFlag());
					rankInfo.explain(docInfo2);
					hasNext2 = clause2.next(docInfo2);
				}else{
					//우측 단어가 왼쪽에 모두 포함이면 버린다. 
					if(docInfo1.isMatchContains(docInfo2.matchFlag())){
						rankInfo.init(doc1, docInfo1.score(), docInfo1.hit());
						rankInfo.addMatchFlag(docInfo1.matchFlag());
						rankInfo.explain(docInfo1);
					} else {
						rankInfo.init(doc1, docInfo1.score() + docInfo2.score(), docInfo1.hit() + docInfo2.hit());
						rankInfo.addMatchFlag(docInfo1.matchFlag());
						rankInfo.addMatchFlag(docInfo2.matchFlag());
						rankInfo.explain(docInfo1);
						rankInfo.explain(docInfo2);
					}
					hasNext1 = clause1.next(docInfo1);
					hasNext2 = clause2.next(docInfo2);
						
				}
				return true;
			}
			
			if(hasNext1){
				rankInfo.init(doc1, docInfo1.score(), docInfo1.hit());
				rankInfo.addMatchFlag(docInfo1.matchFlag());
				rankInfo.explain(docInfo1);
				hasNext1 = clause1.next(docInfo1);
				return true;
			}
		
			if(hasNext2){
				rankInfo.init(doc2, docInfo2.score(), docInfo2.hit());
				rankInfo.addMatchFlag(docInfo2.matchFlag());
				rankInfo.explain(docInfo2);
				hasNext2 = clause2.next(docInfo2);
				return true;
			}
			
		}
		
		return false;
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
        writer.append(indentSpace).append("[LOR]\n");
        if(clause1 != null){
            clause1.printTrace(writer, indent, depth + 1);
        }
        if(clause2 != null){
            clause2.printTrace(writer, indent, depth + 1);
        }
    }

	@Override
	public OperatedClause[] children() {
		return new OperatedClause[] {
			clause1,
			clause2
		};
	}
}
