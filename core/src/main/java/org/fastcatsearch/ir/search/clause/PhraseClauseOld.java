package org.fastcatsearch.ir.search.clause;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.*;
import org.apache.lucene.analysis.tokenattributes.FeatureAttribute.FeatureType;
import org.apache.lucene.util.CharsRef;
import org.fastcatsearch.ir.io.CharVector;
import org.fastcatsearch.ir.io.CharVectorTokenizer;
import org.fastcatsearch.ir.query.HighlightInfo;
import org.fastcatsearch.ir.query.RankInfo;
import org.fastcatsearch.ir.query.Term;
import org.fastcatsearch.ir.query.Term.Option;
import org.fastcatsearch.ir.search.PostingReader;
import org.fastcatsearch.ir.search.SearchIndexReader;
import org.fastcatsearch.ir.search.method.NormalSearchMethod;
import org.fastcatsearch.ir.search.method.SearchMethod;
import org.fastcatsearch.ir.settings.IndexRefSetting;
import org.fastcatsearch.ir.settings.IndexSetting;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;

@Deprecated
public class PhraseClauseOld extends OperatedClause {

	private MultiTermOperatedClause operatedClause;

	public PhraseClauseOld(SearchIndexReader searchIndexReader, Term term, HighlightInfo highlightInfo) {
		super(searchIndexReader.indexId());
		String indexId = searchIndexReader.indexId();
		String termString = term.termString();
		int weight = term.weight();
		Option option = term.option();

		CharVector fullTerm = new CharVector(termString);
		Analyzer analyzer = searchIndexReader.getQueryAnalyzerFromPool();

		IndexSetting indexSetting = searchIndexReader.indexSetting();
		if (highlightInfo != null) {
			String queryAnalyzerId = indexSetting.getQueryAnalyzer();
			for (IndexRefSetting refSetting : indexSetting.getFieldList()) {
				highlightInfo.add(refSetting.getRef(), refSetting.getIndexAnalyzer(), queryAnalyzerId, term.termString(),term.option().value());
			}
		}
		
		operatedClause = new MultiTermOperatedClause(indexId, searchIndexReader.indexFieldOption().isStorePosition());
		
		try {
			CharVectorTokenizer charVectorTokenizer = new CharVectorTokenizer(fullTerm);
			CharTermAttribute termAttribute = null;
			CharsRefTermAttribute refTermAttribute = null;
			PositionIncrementAttribute positionAttribute = null;
			SynonymAttribute synonymAttribute = null;
			StopwordAttribute stopwordAttribute = null;
			FeatureAttribute featureAttribute = null;
			int positionOffset = 0;

			// 어절로 분리.
			while (charVectorTokenizer.hasNext()) {
				CharVector eojeol = charVectorTokenizer.next();

				TokenStream tokenStream = analyzer.tokenStream(indexId, eojeol.getReader());
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
				CharTermAttribute charTermAttribute = tokenStream.getAttribute(CharTermAttribute.class);

				if (tokenStream.hasAttribute(SynonymAttribute.class)) {
					synonymAttribute = tokenStream.getAttribute(SynonymAttribute.class);
				}
				if (tokenStream.hasAttribute(StopwordAttribute.class)) {
					stopwordAttribute = tokenStream.getAttribute(StopwordAttribute.class);
				}
				if (tokenStream.hasAttribute(FeatureAttribute.class)) {
					featureAttribute = tokenStream.getAttribute(FeatureAttribute.class);
				}
				// PosTagAttribute tagAttribute =
				// tokenStream.getAttribute(PosTagAttribute.class);

				FeatureType prevType = null;
				CharVector token = null;
				while (tokenStream.incrementToken()) {

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
						token = new CharVector(charTermAttribute.buffer(), 0, charTermAttribute.length(), indexSetting.isIgnoreCase());
					}

					// logger.debug("token = {}", token);
					// token.toUpperCase();
					//
					// stopword
					//
					if (option.useStopword() && stopwordAttribute != null && stopwordAttribute.isStopword()) {
						logger.debug("stopword : {}", token);
						continue;
					}
					
					FeatureType featureType = null;
					if (featureAttribute != null) {
						featureType = featureAttribute.type();
						if (featureType == FeatureType.APPEND) {
							if (prevType != null && (prevType == FeatureType.MAIN || prevType == FeatureType.APPEND)) {
								// 이전 타입이 main 또는 계속해서 append이면, 부가점수를 올려준다.
								// TODO
							} else {
								// 버린다. 즉, 검색시 무시된다.
							}
						} else {
							// main 결과 셋으로 사용.
							// TODO
						}
						prevType = featureType;
					}

					int queryPosition = 0;
					if (positionAttribute != null) {
						int position = positionAttribute.getPositionIncrement();
						queryPosition = positionOffset + position; //
						positionOffset = position + 2; // 다음 position은 +2 부터 할당한다. 공백도 1만큼 차지.
					}

					logger.debug("PHRASE TERM {} >> [{}] [{}, {}] ", token, featureType, positionAttribute.getPositionIncrement(), queryPosition);
					SearchMethod searchMethod = searchIndexReader.createSearchMethod(new NormalSearchMethod());
					PostingReader postingReader = searchMethod.search(indexId, token, queryPosition, weight);
//					OperatedClause clause = new TermOperatedClause(postingDocs, weight);
//					OperatedClause clause = new TermOperatedClause(postingReader);
					operatedClause.addTerm(postingReader);

//					if (operatedClause == null) {
//						operatedClause = clause;
//					} else {
//						operatedClause = new AndOperatedClause(operatedClause, clause);
//					}
				}

			}
		} catch (IOException e) {
			logger.error("", e);
		} finally {
			searchIndexReader.releaseQueryAnalyzerToPool(analyzer);
		}
	}

	@Override
	protected boolean nextDoc(RankInfo docInfo) {
		if (operatedClause == null) {
			return false;
		}
		return operatedClause.next(docInfo);
	}

	@Override
	public void close() {
		if(operatedClause != null){
			operatedClause.close();
		}
	}

    @Override
    public void printTrace(Writer writer, int indent, int depth) throws IOException {

    }

    @Override
	protected void initClause(boolean explain) {
		operatedClause.init(explanation != null ? explanation.createSubExplanation() : null);
	}


//	@Override
//	protected void initExplanation() {
//		if(operatedClause != null) {
//			operatedClause.setExplanation(explanation.createSub1());
//		}
//	}

}
