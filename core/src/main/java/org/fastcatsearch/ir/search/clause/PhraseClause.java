package org.fastcatsearch.ir.search.clause;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.AnalyzerOption;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharsRefTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.StopwordAttribute;
import org.apache.lucene.analysis.tokenattributes.SynonymAttribute;
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

public class PhraseClause extends OperatedClause {

    private String termString;
    private SearchIndexReader searchIndexReader;
    private OperatedClause operatedClause;
    private int weight;

    private int tokenSize;

    public PhraseClause(SearchIndexReader searchIndexReader, Term term, HighlightInfo highlightInfo) {
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

            operatedClause = search(indexId, fullTerm, term.getProximity(), term.type(), indexSetting, analyzer, analyzerOption);

        } catch (IOException e) {
            logger.error("", e);
        } finally {
            searchIndexReader.releaseQueryAnalyzerToPool(analyzer);
        }
    }

    private OperatedClause search(String indexId, CharVector fullTerm, int proximity, Type type, IndexSetting indexSetting, Analyzer analyzer, AnalyzerOption analyzerOption) throws IOException {
        logger.debug("############ search Term > {}", fullTerm);
        OperatedClause operatedClause = null;

        CharTermAttribute termAttribute = null;
        CharsRefTermAttribute refTermAttribute = null;
        PositionIncrementAttribute positionAttribute = null;
        StopwordAttribute stopwordAttribute = null;
        SynonymAttribute synonymAttribute = null;

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
        if (tokenStream.hasAttribute(SynonymAttribute.class)) {
            synonymAttribute = tokenStream.getAttribute(SynonymAttribute.class);
        }

        CharVector token = null;

        int queryPosition = 0;

        while (tokenStream.incrementToken()) {

            if (stopwordAttribute != null && stopwordAttribute.isStopword()) {
//				logger.debug("stopword");
                continue;
            }

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
            if(logger.isDebugEnabled()) {
                logger.debug("token > {} queryPosition = {}, isIgnoreCase = {} analyzer= {}", token, queryPosition, token.isIgnoreCase(), analyzer.getClass().getSimpleName());
            }
//			logger.debug("token = {} : {}", token, queryPosition);

            SearchMethod searchMethod = searchIndexReader.createSearchMethod(new NormalSearchMethod());
            PostingReader postingReader = searchMethod.search(indexId, token, queryPosition, weight);

            OperatedClause clause = new TermOperatedClause(indexId, token.toString(), postingReader, tokenSize);
            // 유사어 처리
            if(synonymAttribute != null) {
                clause = applySynonym(clause, searchIndexReader, synonymAttribute, indexId, queryPosition, tokenSize);
            }
            if (operatedClause == null) {
                operatedClause = clause;
            } else {
                operatedClause = new AndOperatedClause(operatedClause, clause, proximity, true);
            }
            tokenSize++;
        }

        StringWriter writer = new StringWriter();
        operatedClause.printTrace(writer, 4, 0);
        if(logger.isDebugEnabled()) {
            logger.debug("{}", writer.toString());
        }

        return operatedClause;
    }


    int count = 0;
    RankInfo localRankInfo = new RankInfo();
    @Override
    protected boolean nextDoc(RankInfo rankInfo) {
        if (operatedClause == null) {
            return false;
        }

        localRankInfo.clearOccurrence();
        if(operatedClause.next(localRankInfo)) {
            //TODO 여기서 단어출현 위치에 따른 점수계산 로직수행.



            List<TermOccurrences> termOccurrencesList = localRankInfo.getTermOccurrencesList();

            TermOccurrenceScorer.calculateScore(termOccurrencesList, tokenSize);

            logger.debug("=[{}] {} >> Occur[{}]========", count++, termString, termOccurrencesList.size());
            for(int i =0; i < termOccurrencesList.size(); i++) {
                TermOccurrences termOccurrences = termOccurrencesList.get(i);

                logger.debug(" > {}", termOccurrences);

            }

            rankInfo.init(localRankInfo);
            return true;
        } else {
            return false;
        }

//        return operatedClause.next(rankInfo);
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
        writer.append(indentSpace).append("[PHRASE]\n");
        operatedClause.printTrace(writer, indent, depth + 1);

    }


    private OperatedClause applySynonym(OperatedClause clause,
                                        SearchIndexReader searchIndexReader,
                                        SynonymAttribute synonymAttribute, String indexId, int queryPosition,
                                        int termSequence) throws IOException {

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
                            OperatedClause localClause = new TermOperatedClause(indexId, synonym.toString(), localPostingReader, termSequence);

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
                        OperatedClause localClause = new TermOperatedClause(indexId, localToken.toString(), localPostingReader, termSequence);

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
                        OperatedClause localClause = new TermOperatedClause(indexId, localToken.toString(), localPostingReader, termSequence);

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
