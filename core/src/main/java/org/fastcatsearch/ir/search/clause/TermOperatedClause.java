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
import org.fastcatsearch.ir.search.PostingDoc;
import org.fastcatsearch.ir.search.PostingReader;

public class TermOperatedClause extends OperatedClause {
	private static final int SCORE_BASE = 10000;
	private PostingReader postingReader;
	private int segmentDF;
	private int documentCount;

	private String termString;
	private int termSequence;
	
	public TermOperatedClause(String indexId, String termString, PostingReader postingReader) throws IOException {
		this(indexId, termString, postingReader, 0);
	}
	public TermOperatedClause(String indexId, String termString, PostingReader postingReader, int termSequence) throws IOException {
		super(indexId);
		this.termString = termString;

		if (postingReader != null) {
			this.postingReader = postingReader;
			this.segmentDF = postingReader.size();
			this.documentCount = postingReader.documentCount();
			//termString = postingReader.term().toString();
			this.termSequence = termSequence;
            logger.debug(">>>>>>>>>> {} [{}]", termString, postingReader.termPosition());
		} else {
            logger.debug(">>>>>>>>>> {} XXX", termString);
        }
	}

	protected boolean nextDoc(RankInfo rankInfo) {
		if (postingReader == null) {
			rankInfo.setEmpty();
			return false;
		}
		if (postingReader.hasNext()) {
			PostingDoc postingDoc = postingReader.next();
			int score = 0;
			
			if(postingReader.weight() > 0) {
				score = postingReader.weight();
			} else if(postingReader.weight() == -1) {
				float tf = 2.2f * postingDoc.tf() / (2.0f + postingDoc.tf());
				float idf = (float) Math.log(documentCount / segmentDF);
				score = (int) (tf * idf * SCORE_BASE);
			}
//            logger.debug("TermOP >> {} doc[{}] score[{}] hit[{}] pos[{}]", termString, postingDoc.docNo(), score, termString.length(), postingDoc.positions());
//			rankInfo.init(postingDoc.docNo(), score, postingDoc.tf(), postingDoc.positions());
            rankInfo.init(postingDoc.docNo(), score, termString.length() * 3, postingDoc.positions());
			rankInfo.addMatchSequence(termSequence);
			if(isExplain()){
				rankInfo.explain(id, score, postingReader.term().toString());
			}
			return true;
		} else {
			rankInfo.setEmpty();
			return false;
		}
	}

	@Override
	public String toString() {
		if (postingReader != null) {
			return "[" + getClass().getSimpleName() + "]" + postingReader.term() + ":" + postingReader.size();
		} else {
			return "[" + getClass().getSimpleName() + "] null";
		}
	}

	@Override
	public void close() {
		if (postingReader != null) {
			postingReader.close();
		}
	}

	@Override
	protected void initClause(boolean explain) {
		if(explanation != null) {
			if(postingReader != null) {
				explanation.setTerm(postingReader.term().toString());
			}
		}
	}

//	@Override
//	protected void initExplanation() {
//		explanation.setTerm(postingReader.term().toString());
//	}

	@Override
	public String term() {
		return termString;
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
		int size = 0;
		if(postingReader!=null) {
			size = postingReader.size();
		}
        writer.append(indentSpace).append("[TERM] ").append(termString).append(" [").append(String.valueOf(size)).append("] ").append(id);
	}
}
