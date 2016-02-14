package org.fastcatsearch.ir.search.clause;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.AnalyzerOption;
import org.apache.lucene.analysis.tokenattributes.AdditionalTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharsRefTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.StopwordAttribute;
import org.apache.lucene.analysis.tokenattributes.SynonymAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.CharsRef;
import org.fastcatsearch.ir.io.CharVector;
import org.fastcatsearch.ir.query.HighlightInfo;
import org.fastcatsearch.ir.query.RankInfo;
import org.fastcatsearch.ir.query.Term;
import org.fastcatsearch.ir.query.Term.Option;
import org.fastcatsearch.ir.query.Term.Type;
import org.fastcatsearch.ir.search.PostingReader;
import org.fastcatsearch.ir.search.SearchIndexReader;
import org.fastcatsearch.ir.search.method.NormalSearchMethod;
import org.fastcatsearch.ir.search.method.SearchMethod;
import org.fastcatsearch.ir.settings.IndexRefSetting;
import org.fastcatsearch.ir.settings.IndexSetting;
import org.fastcatsearch.ir.util.CharVectorUtils;

public class BooleanClause extends OperatedClause {

	private String termString;
	private SearchIndexReader searchIndexReader;
	private OperatedClause operatedClause;
	private int weight;
	
	public BooleanClause(SearchIndexReader searchIndexReader, Term term, HighlightInfo highlightInfo) {
		this(searchIndexReader, term, highlightInfo, null);
	}
	public BooleanClause(SearchIndexReader searchIndexReader, Term term, HighlightInfo highlightInfo, String requestTypeAttribute) {
		super(searchIndexReader.indexId());
		this.searchIndexReader = searchIndexReader;
		String indexId = searchIndexReader.indexId();
		String termString = term.termString();
		this.termString = termString;
		this.weight = term.weight();
		Option searchOption = term.option();
		CharVector fullTerm = new CharVector(termString);
		Analyzer analyzer = searchIndexReader.getQueryAnalyzerFromPool();
		
		IndexSetting indexSetting = searchIndexReader.indexSetting();
		if (highlightInfo != null && searchOption.useHighlight()) {
			String queryAnalyzerId = indexSetting.getQueryAnalyzer();
			for (IndexRefSetting refSetting : indexSetting.getFieldList()) {
				highlightInfo.add(refSetting.getRef(), refSetting.getIndexAnalyzer(), queryAnalyzerId, term.termString(), searchOption.value());
			}
		}
		try {
			//검색옵션에 따라 analyzerOption도 수정.
			AnalyzerOption analyzerOption = new AnalyzerOption();
			analyzerOption.useStopword(searchOption.useStopword());
			analyzerOption.useSynonym(searchOption.useSynonym());
			analyzerOption.setForQuery();

			operatedClause = search(indexId, fullTerm, term.getProximity(), term.type(), indexSetting, analyzer, analyzerOption, requestTypeAttribute);
			
		} catch (IOException e) {
			logger.error("", e);
		} finally {
			searchIndexReader.releaseQueryAnalyzerToPool(analyzer);
		}
	}
	
	private OperatedClause search(String indexId, CharVector fullTerm, int proximity, Type type, IndexSetting indexSetting, Analyzer analyzer, AnalyzerOption analyzerOption, String requestTypeAttribute) throws IOException {
		logger.debug("############ search Term > {}", fullTerm);
		OperatedClause operatedClause = null;
		OperatedClause finalClause = null;
		
		CharTermAttribute termAttribute = null;
		CharsRefTermAttribute refTermAttribute = null;
		PositionIncrementAttribute positionAttribute = null;
		StopwordAttribute stopwordAttribute = null;
		TypeAttribute typeAttribute = null;
		AdditionalTermAttribute additionalTermAttribute = null;
		
		SynonymAttribute synonymAttribute = null;
		OffsetAttribute offsetAttribute = null;
		
		TokenStream tokenStream = analyzer.tokenStream(indexId, fullTerm.getReader(), analyzerOption);
		tokenStream.reset();
		
		if (tokenStream.hasAttribute(CharsRefTermAttribute.class)) {
			refTermAttribute = tokenStream.getAttribute(CharsRefTermAttribute.class);
		}
		if (tokenStream.hasAttribute(CharTermAttribute.class)) {
			termAttribute = tokenStream.getAttribute(CharTermAttribute.class);
		}
		if (tokenStream.hasAttribute(PositionIncrementAttribute.class)) {
			positionAttribute = tokenStream.getAttribute(PositionIncrementAttribute.class);
		}
		if (tokenStream.hasAttribute(StopwordAttribute.class)) {
			stopwordAttribute = tokenStream.getAttribute(StopwordAttribute.class);
		}
		if (tokenStream.hasAttribute(TypeAttribute.class)) {
			typeAttribute = tokenStream.getAttribute(TypeAttribute.class);
		}
		if (tokenStream.hasAttribute(AdditionalTermAttribute.class)) {
			additionalTermAttribute = tokenStream.getAttribute(AdditionalTermAttribute.class);
		}
		if (tokenStream.hasAttribute(SynonymAttribute.class)) {
			synonymAttribute = tokenStream.getAttribute(SynonymAttribute.class);
		}
		
		if (tokenStream.hasAttribute(OffsetAttribute.class)) {
			offsetAttribute = tokenStream.getAttribute(OffsetAttribute.class);
		}

		CharVector token = null;
		AtomicInteger termSequence = new AtomicInteger();
		
		int queryDepth = 0;
		
		int queryPosition = 0;

		while (tokenStream.incrementToken()) {

			//요청 타입이 존재할때 타입이 다르면 단어무시.
			if(requestTypeAttribute != null && typeAttribute != null){
				if(requestTypeAttribute != typeAttribute.type()){
					continue;
				}
			}
			/* 
			 * stopword 
			 * */
			if (stopwordAttribute != null && stopwordAttribute.isStopword()) {
//				logger.debug("stopword");
				continue;
			}

			/*
			 * Main 단어는 tf를 적용하고, 나머지는 tf를 적용하지 않는다.
			 * */
			if (refTermAttribute != null) {
				CharsRef charRef = refTermAttribute.charsRef();

				if (charRef != null) {
					char[] buffer = new char[charRef.length()];
					System.arraycopy(charRef.chars, charRef.offset, buffer, 0, charRef.length);
					token = new CharVector(buffer, 0, buffer.length, indexSetting.isIgnoreCase());
				} else if (termAttribute != null && termAttribute.buffer() != null) {
					token = new CharVector(termAttribute.buffer(), indexSetting.isIgnoreCase());
				}
			} else {
				token = new CharVector(termAttribute.buffer(), 0, termAttribute.length(), indexSetting.isIgnoreCase());
			}
			
			queryPosition = positionAttribute != null ? positionAttribute.getPositionIncrement() : 0;
            logger.debug("token > {} queryPosition = {}, isIgnoreCase = {} analyzer= {}", token, queryPosition, token.isIgnoreCase(), analyzer.getClass().getSimpleName());
//			logger.debug("token = {} : {}", token, queryPosition);

			SearchMethod searchMethod = searchIndexReader.createSearchMethod(new NormalSearchMethod());
			PostingReader postingReader = searchMethod.search(indexId, token, queryPosition, weight);

			OperatedClause clause = new TermOperatedClause(indexId, token.toString(), postingReader, termSequence.getAndIncrement());
//			logger.debug("Term :{}", token.toString());
			// 유사어 처리
			// analyzerOption에 synonym확장여부가 들어가 있으므로, 여기서는 option을 확인하지 않고,
			// 있으면 그대로 색인하고 유사어가 없으면 색인되지 않는다.
			//
			//isSynonym 일 경우 다시한번 유사어확장을 하지 않는다.
			if(synonymAttribute != null) {
				clause = applySynonym(clause, searchIndexReader, synonymAttribute, indexId, queryPosition, termSequence, type);
			}
			if (operatedClause == null) {
				operatedClause = clause;
				queryDepth ++;
			} else {
				if(type == Type.ALL){
					operatedClause = new AndOperatedClause(operatedClause, clause, proximity);
					queryDepth ++;
				}else if(type == Type.ANY){
					operatedClause = new OrOperatedClause(operatedClause, clause, proximity);
					queryDepth ++;
				}
			}
			
			//추가 확장 단어들.
			if(additionalTermAttribute != null) {
				Iterator<String> termIter = additionalTermAttribute.iterateAdditionalTerms();
				OperatedClause additionalClause = null;
				while(termIter.hasNext()) {
					CharVector localToken = new CharVector(termIter.next().toCharArray(), indexSetting.isIgnoreCase());
					queryPosition = positionAttribute != null ? positionAttribute.getPositionIncrement() : 0;
					searchMethod = searchIndexReader.createSearchMethod(new NormalSearchMethod());
					postingReader = searchMethod.search(indexId, localToken, queryPosition, weight);
					clause = new TermOperatedClause(indexId, localToken.toString(), postingReader, termSequence.getAndIncrement());
					
					if(synonymAttribute!=null) {
						clause = this.applySynonym(clause, searchIndexReader, synonymAttribute, indexId, queryPosition, termSequence, type);
					}
					if ((offsetAttribute.startOffset() == 0 &&
						offsetAttribute.endOffset() == fullTerm.length())) {
						//전체단어동의어 확장어
						finalClause = clause;
					} else {
						//일반확장단어들
					
						if(additionalClause == null) {
							additionalClause = clause;
						} else {
							additionalClause = new OrOperatedClause(additionalClause, clause);
						}
					}
				}
				
				if(logger.isTraceEnabled()) {
					logger.trace("clause:{}", dumpClause(operatedClause));
				}
				if(additionalClause != null) {
					int subSize = additionalTermAttribute.subSize();
					logger.trace("additional term subSize:{}/{} : {}", subSize, queryDepth, additionalTermAttribute);
					if( subSize > 0) {
						//추가텀이 가진 서브텀의 갯수만큼 거슬러 올라가야 한다.
						for(int inx=0;inx<subSize-1; inx++) {
							OperatedClause[] subClause = operatedClause.children();
							
							if(subClause!=null && subClause.length == 2) {
								OperatedClause clause1 = subClause[0]; //
								OperatedClause clause2 = subClause[1];
								if( operatedClause instanceof AndOperatedClause ) {
									if(clause1 instanceof AndOperatedClause) {
										OperatedClause[] subClause2 = clause1.children();
										OperatedClause clause3 = subClause2[0]; //
										OperatedClause clause4 = subClause2[1];
										
										operatedClause = new AndOperatedClause(
												clause3, new AndOperatedClause(clause4, clause2));
										if(logger.isTraceEnabled()) {
											logger.trace("clause:{}", dumpClause(operatedClause));
										}
									}
								}
							}
						}
						
						if(operatedClause instanceof AndOperatedClause) {
							OperatedClause[] subClause = operatedClause.children();
							OperatedClause clause1 = subClause[0];
							OperatedClause clause2 = subClause[1];
							
							//괄호 우선 순위상 최초 추가텀만 따로 처리해 주어야 한다.
							//첫머리에서 발견되는 추가텀은 마지막 괄호에 적용해야 하나
							//두번째 이후 위치에서 발견되는 추가텀 부터는 지역 괄호에 적용해야 함.
							if(subSize == queryDepth) {
								clause2 = new AndOperatedClause(clause1, clause2);
								operatedClause = new OrOperatedClause(clause2, additionalClause);
							} else {
								clause2 = new OrOperatedClause(clause2, additionalClause);
								operatedClause = new AndOperatedClause(clause1, clause2);
							}
						} else if(operatedClause instanceof OrOperatedClause) {
							//simply append in or-operated clause.
							operatedClause = new OrOperatedClause(operatedClause, additionalClause);
						}
						
						if(logger.isTraceEnabled()) {
							logger.trace("clause:{}", dumpClause(operatedClause));
						}
					}
				}
			}
		}
		
		if(finalClause!=null) {
			operatedClause = new OrOperatedClause(operatedClause, finalClause);
		}
		if(logger.isTraceEnabled()) {
			logger.trace("clause:{}", dumpClause(operatedClause));
		}
		
//		if(logger.isTraceEnabled() && operatedClause!=null) {
//			ByteArrayOutputStream baos = new ByteArrayOutputStream();
//			PrintStream traceStream = new PrintStream(baos);
//			operatedClause.printTrace(traceStream, 0);
//			logger.trace("OperatedClause stack >> \n{}", baos.toString());
//		}
		return operatedClause;
	}
	public static String dumpClause(OperatedClause clause) {
		StringBuilder sb = new StringBuilder();
		OperatedClause[] children = clause.children();
		if(children!=null) {
			if(children.length == 2) {
				if(clause instanceof AndOperatedClause) {
					sb.append("(");
					if(children[0] instanceof TermOperatedClause) {
						sb.append(((TermOperatedClause)children[0]).term());
					} else if(children[0].children()!=null) {
						sb.append(dumpClause(children[0]));
					}
					sb.append(" and ");
					if(children[1] instanceof TermOperatedClause) {
						sb.append(((TermOperatedClause)children[1]).term());
					} else if(children[1].children()!=null) {
						sb.append(dumpClause(children[1]));
					}
					sb.append(")");
				} else if(clause instanceof OrOperatedClause) {
					sb.append("(");
					if(children[0] instanceof TermOperatedClause) {
						sb.append(((TermOperatedClause)children[0]).term());
					} else if(children[0].children()!=null) {
						sb.append(dumpClause(children[0]));
					}
					sb.append(" or ");
					if(children[1] instanceof TermOperatedClause) {
						sb.append(((TermOperatedClause)children[1]).term());
					} else if(children[1].children()!=null) {
						sb.append(dumpClause(children[1]));
					}
					sb.append(")");
					
				} else {
					sb.append("(");
					if(children[0] instanceof TermOperatedClause) {
						sb.append(((TermOperatedClause)children[0]).term());
					} else if(children[0].children()!=null) {
						sb.append(dumpClause(children[0]));
					}
					sb.append(" ? ");
					if(children[1] instanceof TermOperatedClause) {
						sb.append(((TermOperatedClause)children[1]).term());
					} else if(children[1].children()!=null) {
						sb.append(dumpClause(children[1]));
					}
					sb.append(")");
				}
			} else if(children.length == 1) {
				
				if(clause instanceof TermOperatedClause) {
					sb.append(((TermOperatedClause)clause).term());
				} else {
					sb.append("[").append(dumpClause(children[0])).append("]");
				}
			}
		}
		
		return sb.toString();
	}
	@Override
	protected boolean nextDoc(RankInfo rankInfo) {
		if (operatedClause == null) {
			return false;
		}
		return operatedClause.next(rankInfo);
	}

	@Override
	public void close() {
		if (operatedClause != null) {
			operatedClause.close();
		}
	}
	@Override
	protected void initClause(boolean explain) {
		if (operatedClause != null) {
			operatedClause.init(explanation != null ? explanation.createSubExplanation() : null);
		}
	}
	
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
        writer.append(indentSpace).append("[OR]");
		operatedClause.printTrace(writer, indent, depth + 1);
		
	}
	

	private OperatedClause applySynonym(OperatedClause clause, 
			SearchIndexReader searchIndexReader,
			SynonymAttribute synonymAttribute, String indexId, int queryPosition, 
			AtomicInteger termSequence, Type type) throws IOException {
		
		@SuppressWarnings("unchecked")
		List<Object> synonymObj = synonymAttribute.getSynonyms();
		if(synonymObj != null) {
			OperatedClause synonymClause = null;
			for(Object obj : synonymObj) {
				if(obj instanceof CharVector) {
					CharVector localToken = (CharVector)obj;
					localToken.setIgnoreCase();
                    if(localToken.hasWhitespaces()) {
                        List<CharVector> synonyms = CharVectorUtils.splitByWhitespace(localToken);
                        OperatedClause extractedClause = null;
                        /*
                         * 유사어에 공백이 포함된 경우 여러단어로 나누어 AND 관계로 추가한다.
                         * '서울대 => 서울 대학교'
                         * 와 같은 관계가 해당된다.
                         */

                        for(CharVector synonym : synonyms) {
                            SearchMethod localSearchMethod = searchIndexReader.createSearchMethod(new NormalSearchMethod());
                            PostingReader localPostingReader = localSearchMethod.search(indexId, synonym, queryPosition, weight);
                            OperatedClause localClause = new TermOperatedClause(indexId, synonym.toString(), localPostingReader, termSequence.getAndIncrement());

                            if(extractedClause == null) {
                                extractedClause = localClause;
                            } else {
                                //공백구분 유사어는 AND 관계가 맞다. 15.12.24 swsong
                                extractedClause = new AndOperatedClause(extractedClause, localClause);
                            }
                        }
                        if(synonymClause == null) {
                            synonymClause = extractedClause;
                        } else {
                            synonymClause = new OrOperatedClause(synonymClause, extractedClause);
                        }
                    } else {
                        SearchMethod localSearchMethod = searchIndexReader.createSearchMethod(new NormalSearchMethod());
                        PostingReader localPostingReader = localSearchMethod.search(indexId, localToken, queryPosition, weight);
                        OperatedClause localClause = new TermOperatedClause(indexId, localToken.toString(), localPostingReader, termSequence.getAndIncrement());

                        if (synonymClause == null) {
                            synonymClause = localClause;
                        } else {
                            synonymClause = new OrOperatedClause(synonymClause, localClause);
                        }
                    }
				} else if(obj instanceof List) {
					@SuppressWarnings("unchecked")
					List<CharVector>synonyms = (List<CharVector>)obj; 
					OperatedClause extractedClause = null;
					//유사어가 여러단어로 분석될경우
					for(CharVector localToken : synonyms) {
						localToken.setIgnoreCase();
						SearchMethod localSearchMethod = searchIndexReader.createSearchMethod(new NormalSearchMethod());
						PostingReader localPostingReader = localSearchMethod.search(indexId, localToken, queryPosition, weight);
						OperatedClause localClause = new TermOperatedClause(indexId, localToken.toString(), localPostingReader, termSequence.getAndIncrement());
						
						if(extractedClause == null) {
							extractedClause = localClause;
						} else {
                            //공백구분 유사어는 AND 관계가 맞다. 15.12.24 swsong
                            extractedClause = new AndOperatedClause(extractedClause, localClause);
						}
					}
					if(synonymClause == null) {
						synonymClause = extractedClause;
					} else {
						synonymClause = new OrOperatedClause(synonymClause, extractedClause);
					}
				}
			}
			if(synonymClause != null) {
                int position = clause.getPosition();
				clause = new OrOperatedClause(clause, synonymClause);
                clause.setPosition(position);
			}
		}
		
		return clause;
	}
	@Override
	public OperatedClause[] children() {
		return new OperatedClause[] { operatedClause };
	}
}
