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

import java.io.PrintStream;
import java.io.Writer;

import org.fastcatsearch.ir.query.RankInfo;
import org.fastcatsearch.ir.search.ClauseExplanation;

public class WeightedOperatedClause extends OperatedClause {
	private OperatedClause mainClause;
	private OperatedClause weightClause;
	private boolean hasNext1 = true;
	private boolean hasNext2 = true;

	private RankInfo docInfo1 = new RankInfo();
	private RankInfo docInfo2 = new RankInfo();
	
	public WeightedOperatedClause(OperatedClause mainClause, OperatedClause weightClause) {
		this(mainClause, weightClause, null);
	}
	public WeightedOperatedClause(OperatedClause mainClause, OperatedClause weightClause, ClauseExplanation explanation) {
		super("WEIGHT");
		this.mainClause = mainClause;
		this.weightClause = weightClause;
	}

	protected boolean nextDoc(RankInfo docInfo) {
		
		hasNext1 = mainClause.next(docInfo1);
		
		if (hasNext1) {
			int doc1 = docInfo1.docNo();
			int doc2 = docInfo2.docNo();
			int score1 = docInfo1.score();
			int score2 = docInfo2.score();
			while (hasNext1 && hasNext2 && (doc1 != doc2)) {
				while (hasNext1 && (doc1 < doc2)) {
					docInfo.init(doc1, score1);
					return true;
				}
				while (hasNext2 && (doc1 > doc2)) {
					hasNext2 = weightClause.next(docInfo2);
					doc2 = docInfo2.docNo();
					score2 = docInfo2.score();
				}
			}

			if (hasNext1 && hasNext2 && (doc1 == doc2)) {
				docInfo.init(doc1, score1 + score2);
				hasNext2 = weightClause.next(docInfo2);
				return true;
			}
			if (hasNext1){
				docInfo.init(doc1, score1);
				return true;
			}
			return false;
		}

		// mainClause가 끝나면 더이상 없는것이다.
		return false;
	}

	@Override
	public String toString() {
		return "[" + getClass().getSimpleName() + "]" + (mainClause != null ? mainClause.toString() : "null") + " / " + (weightClause != null ? weightClause.toString() : "null");
	}

	@Override
	public void close() {
		if(mainClause != null){
			mainClause.close();
		}
		if(weightClause != null){
			weightClause.close();
		}		
	}

    @Override
    public void printTrace(Writer writer, int indent, int depth) {

    }

    @Override
	protected void initClause(boolean explain) {
		mainClause.init(explanation != null ? explanation.createSubExplanation() : null);
		weightClause.init(explanation != null ? explanation.createSubExplanation() : null);
		hasNext2 = weightClause.next(docInfo2);
	}

//	@Override
//	protected void initExplanation() {
//		if(mainClause != null) {
//			mainClause.setExplanation(explanation.createSub1());
//		}
//		if(weightClause != null) {
//			weightClause.setExplanation(explanation.createSub2());
//		}		
//	}

}
