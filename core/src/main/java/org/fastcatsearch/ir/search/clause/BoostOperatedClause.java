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
 * mainClause 를 기준으로 boostClause 에 일치하는 문서가 있다면 점수를 올려준다.
 * 결과 set은 mainClause의 것이 그대로 유지되며, boostClause는 점수에만 영향을 준다.
 * 
 * */
public class BoostOperatedClause extends OperatedClause {
	private OperatedClause mainClause;
	private OperatedClause boostClause;
	private RankInfo docInfo1 = new RankInfo();
	private RankInfo docInfo2 = new RankInfo();
	
	public BoostOperatedClause(OperatedClause mainClause, OperatedClause boostClause) {
		super("BOOST");
		this.mainClause = mainClause;
		this.boostClause = boostClause;
	}

	protected boolean nextDoc(RankInfo rankInfo) {
		
		int newScore = 0;
		while(mainClause.next(docInfo1)){
			int doc1 = docInfo1.docNo();
			newScore = docInfo1.score();
			
			while(docInfo2.docNo() != -1 && docInfo2.docNo() < doc1){
				if(!boostClause.next(docInfo2)){
					//끝이면 탈출.
					break;
				}
			}
			
			if(doc1 == docInfo2.docNo()){
				newScore += docInfo2.score();
				rankInfo.explain(docInfo2);
			}
			
			rankInfo.init(doc1, newScore);
			rankInfo.explain(docInfo1);
			return true; 
			
		}
		
		return false;
	}
	
	@Override
	public String toString() {
		return "[" + getClass().getSimpleName() + "]" + (mainClause != null ? mainClause.toString() : "null") + " / " + (boostClause != null ? boostClause.toString() : "null");
	}

	@Override
	public void close() {
		if(mainClause != null){
			mainClause.close();
		}
		if(boostClause != null){
			boostClause.close();
		}
	}

	@Override
	protected void initClause(boolean explain) {
		mainClause.init(explanation != null ? explanation.createSubExplanation() : null);
		boostClause.init(explanation != null ? explanation.createSubExplanation() : null);
		boostClause.next(docInfo2);
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

        writer.append(indentSpace).append("[MAIN]\n");
		if(mainClause != null) {
			mainClause.printTrace(writer, indent, depth + 1);
		}
        writer.append(indentSpace).append("[BOOST]\n");
		if(boostClause != null) {
			boostClause.printTrace(writer, indent, depth + 1);
		}
	}

//	@Override
//	protected void initExplanation() {
//		if(mainClause != null) {
//			mainClause.setExplanation(explanation.createSub1());
//		}
//		if(boostClause != null) {
//			boostClause.setExplanation(explanation.createSub2());
//		}
//	}

}
