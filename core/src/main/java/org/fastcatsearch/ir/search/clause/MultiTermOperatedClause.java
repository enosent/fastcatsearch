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
import java.util.List;

import org.fastcatsearch.ir.query.RankInfo;
import org.fastcatsearch.ir.search.CollectedEntry;
import org.fastcatsearch.ir.search.PostingDoc;
import org.fastcatsearch.ir.search.PostingReader;
import org.fastcatsearch.ir.search.TermDocCollector;
import org.fastcatsearch.ir.search.TermDocTreeReader;
import org.fastcatsearch.ir.search.PostingDocs;
import org.fastcatsearch.ir.search.posting.PostingDocsTreeNode;

public class MultiTermOperatedClause extends OperatedClause {
	private static final int SCORE_BASE = 10000;
	private TermDocTreeReader termDocTreeReader;
	private int termCount;
	private TermDocCollector termDocCollector;
	private boolean storePosition;

	public MultiTermOperatedClause() {
		this(null, false);
	}

	public MultiTermOperatedClause(String indexId, boolean storePosition) {
		super(indexId);
		this.storePosition = storePosition;
		termDocTreeReader = new TermDocTreeReader();
	}

	public void addTerm(PostingReader postingReader) {
		addTerm(postingReader, null);
	}

	public void addTerm(PostingReader postingReader, List<PostingDocs> synonymList) {

		if (postingReader != null) {
			termDocTreeReader.addNode(new PostingDocsTreeNode(postingReader));
			termCount++;
		}

		// PostingDocs sysnonymTermDocs = null;
		// if(synonymList != null){
		// if(synonymList.size() == 1){
		// sysnonymTermDocs = synonymList.get(0);
		// }else{
		// sysnonymTermDocs = new
		// PostingDocsMerger(synonymList).merge(termDocs.term(), 1024);
		// }
		// termDocTreeReader.addNode(new PostingDocsTreeNode(sysnonymTermDocs,
		// queryPosition, true));
		// termCount++;
		// }

	}

	protected boolean nextDoc(RankInfo docInfo) {
		if (termDocCollector == null) {
			termDocCollector = new TermDocCollector(termCount);
		}

		// 동일문서번호에 대한 TermDoc list를 리턴받는다.
		int docNo = -1;
		while (true) {

			while (true) {
				termDocCollector.clear();
				docNo = termDocTreeReader.next(termDocCollector);
				if (docNo == -1 || (((float) termDocCollector.size()) / ((float) termDocCollector.capasity()) > 0.7f)) {
					break;
				}
			}

			if (docNo < 0) {
				return false;
			} else {
				// logger.debug(">> phrase doc={}, term size={}", docNo,
				// totalTermDocList.size());
				// 쿼리와 비교한 여러단어에 대한 점수계산.
				if (storePosition) {
					float proximityScore = 0f;
					int documentScore = 0;
					float sumOfTPI = 0f;
					int windowSize = 2;

//					logger.debug("----------------------------{}", termDocCollector.size());

					int continuosWords = 1;
					for (int i = 0; i < termDocCollector.size(); i++) {
						CollectedEntry entry = termDocCollector.get(i);
						PostingDoc termDoc = entry.termDoc();
						// okapi점수에 tf만 사용. 쿼리점수무시. idf무시.
						// documentScore += (2.2f * (float)termDoc.tf() / (2.0f+ (float)termDoc.tf()));
						documentScore += 1;
//						logger.debug("[{}]doc >> {} : {} >> score {}", entry.term(), termDoc.docNo(), termDoc.tf(), documentScore);

						// score += 1000; //tf 는 무시. 한단어가 여러번 나오는 것은 그다지 중요하지
						// 않음.

						int[] positions = termDoc.positions();
						
						// 인접해있는 windowSize 까지만 봄.
						int j = i + 1;
						if (j < termDocCollector.size()) {

							// TODO doc1과 doc2가 유사어관계면 점수계산하지 않는다. 근데 누구의 유사어인지
							// 확인할 방법이없네..
							// TODO 같은 단어를 두번이상 쿼리에 입려했을 경우 무시하는 로직 필요.?

							CollectedEntry entry2 = termDocCollector.get(j);
							PostingDoc termDoc2 = entry2.termDoc();

//							 logger.debug("### {}[{}] : {}[{}]", entry.term(), termDoc.tf(), entry2.term(), termDoc2.tf());
							int origianlPositionGap = entry2.queryPosition() - entry.queryPosition();

							
							int[] positions2 = termDoc2.positions();
							// logger.debug("pos1= {}", positions);
							// logger.debug("pos2= {}", positions2);
							int minGap = -1;
							for (int i2 = 0; i2 < positions.length; i2++) {
								
								for (int j2 = 0; j2 < positions2.length; j2++) {

									int actualPositionGap = positions2[j2] - positions[i2];
									if(actualPositionGap > 10 || actualPositionGap < -10){
										continue;
									}
//									logger.debug("------- {} - {} = {} : {}", positions[i2], positions2[j2], actualPositionGap, origianlPositionGap);
									
									int gap = 0;
									if(origianlPositionGap > 0){
										if (actualPositionGap > 0) {
											if (actualPositionGap <= origianlPositionGap + windowSize) {
//												logger.debug("OK");
												gap = Math.abs(origianlPositionGap - actualPositionGap);
											} else {
												continue;
											}
										} else {
											if (-actualPositionGap <= origianlPositionGap) {
//												logger.debug("OK2");
												gap = Math.abs(origianlPositionGap + actualPositionGap);
											} else {
												continue;
											}
										}
									}else{
										if (actualPositionGap > 0) {
											if (actualPositionGap <= -origianlPositionGap) {
//												logger.debug("OK3");
												gap = Math.abs(-origianlPositionGap - actualPositionGap);
											} else {
												continue;
											}
										} else {
											if (-actualPositionGap <= -origianlPositionGap + windowSize) {
//												logger.debug("OK4");
												gap = Math.abs(-origianlPositionGap + actualPositionGap);
											} else {
												continue;
											}
										}
									}

									if(minGap == -1 || gap < minGap){
										minGap = gap;
									}
									
								}
							}
							
							if(minGap != -1){
								continuosWords++;
							}
//							int positionGapDiff = origianlPositionGap - actualPositionGap;
							// d(ti, tj) = 원래차이 - 실제차이 => 원래차이와 실제차이가
							// 같을수록 즉 0에 가까울수록 tpi가 높은 점수가 된다.
							// tpi(ti,tj) = 1.0 / d(t1, tj)^2
							// 완벽한 TPRSV 점수를구하려면 문서길이/문서평균길이를 이용한 K 값을
							// 계산해야 하나 여기서는 생략한다.
							float tpi = (float) (1.0 / (1.0 + Math.pow(minGap, 2.0)));
							// logger.debug("------- {} - {} = {} - {} = {} >> {}",
							// positions[i2], positions2[j2],
							// actualPositionGap, origianlPositionGap,
							// positionGapDiff, tpi);
							sumOfTPI += tpi;
							// logger.debug("{}: {} = {}",positions[i2], positions2[j2], tpi);
							
							
						}//if

					}
					
					/*
					 * 인접을 보면서 제외시킨다.
					 */
//					logger.debug("####continuosWords {} >= {} ({})", continuosWords, (termCount/2) + 1, termCount);
					if (continuosWords >= (termCount/2) + 1) {
						float nomalizedDocScore = ((float)documentScore / (float)termCount) * 2.0f;
						// Okapi점수를 계산하여 tpi점수와 더해야 최종 점수가 계산됨.
						proximityScore = 2.2f * sumOfTPI / (2.0f + sumOfTPI);
						int score = (int) ((nomalizedDocScore + proximityScore) * SCORE_BASE);
//						logger.debug("nomalize {} = {} / {} * 2 => {} + {}", score, documentScore, termCount, nomalizedDocScore, proximityScore); 
						docInfo.init(docNo, score);
						break;
					}
				} else {
					// 위치정보가 없으면 tf를 점수로 만든다.
					// Okapi 점수 계산
					float s = 0.0f;
					for (int i = 0; i < termDocCollector.size(); i++) {
						PostingDoc termDoc = termDocCollector.get(i).termDoc();
						s += (2.2f * (float) termDoc.tf() / (2.0f + (float) termDoc.tf()));
					}
					int score = (int) (s * SCORE_BASE);
					logger.debug("추가2 >> docNo={} : {}", docNo, score);
					docInfo.init(docNo, score);
					break;
				}

			}
		}
		// TODO점수가 일정치이하이면 버린다.

		return true;
	}

	@Override
	public void close() {
		termDocTreeReader.close();
	}

    @Override
    public void printTrace(Writer writer, int indent, int depth) throws IOException {

    }

    @Override
	protected void initClause(boolean explain) {
	}


//	@Override
//	protected void initExplanation() {
//	}

}
