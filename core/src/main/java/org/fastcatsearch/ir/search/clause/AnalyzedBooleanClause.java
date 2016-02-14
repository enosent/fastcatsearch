package org.fastcatsearch.ir.search.clause;

import org.fastcatsearch.ir.analysis.TermsEntry;
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

import java.io.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AnalyzedBooleanClause extends OperatedClause {

	private String termString;
	private SearchIndexReader searchIndexReader;
	private OperatedClause operatedClause;
	private int weight;
    private List<TermsEntry> termsEntryList;


	public AnalyzedBooleanClause(SearchIndexReader searchIndexReader, Term term, HighlightInfo highlightInfo) {
		super(searchIndexReader.indexId());
		this.searchIndexReader = searchIndexReader;
		String indexId = searchIndexReader.indexId();

        termsEntryList =  term.getTermsEntryList();

        termString = joinString(termsEntryList);

		this.weight = term.weight();
		Option searchOption = term.option();
		IndexSetting indexSetting = searchIndexReader.indexSetting();
		if (highlightInfo != null && searchOption.useHighlight()) {
			String queryAnalyzerId = indexSetting.getQueryAnalyzer();
			for (IndexRefSetting refSetting : indexSetting.getFieldList()) {
				highlightInfo.add(refSetting.getRef(), refSetting.getIndexAnalyzer(), queryAnalyzerId, termString, searchOption.value());
			}
		}
		try {
			operatedClause = search(indexId, termsEntryList, term.type(), indexSetting);
            if(logger.isDebugEnabled()) {
                StringWriter writer = new StringWriter();
                writer.append("\n");
                printTrace(writer, 4, 0);
                logger.debug("AnalyzedBooleanClause : {}", writer.toString());
            }
		} catch (IOException e) {
			logger.error("", e);
		}
	}

    private String joinString(List<TermsEntry> termsEntryList) {
        StringBuffer sb = new StringBuffer();
        for(TermsEntry e : termsEntryList) {
            for(String t : e.getTerms()) {
                sb.append(t);
                sb.append(" ");
            }
        }
        return sb.toString();
    }


    private OperatedClause search0(List<String> terms, Type type, String indexId, int queryPosition, AtomicInteger termSequence) throws IOException {
        if(terms == null) {
            return null;
        }
        OperatedClause operatedClause = null;
        for(String term : terms) {
            CharVector token = new CharVector(term);
            SearchMethod searchMethod = searchIndexReader.createSearchMethod(new NormalSearchMethod());

            PostingReader postingReader = searchMethod.search(indexId, token, queryPosition, weight);
            OperatedClause clause = new TermOperatedClause(indexId, term, postingReader, termSequence.getAndIncrement());

            if (operatedClause == null) {
                operatedClause = clause;
            } else {
                if(type == Type.ALL){
                    operatedClause = new AndOperatedClause(operatedClause, clause);
                }else if(type == Type.ANY){
                    operatedClause = new OrOperatedClause(operatedClause, clause);
                }
            }
        }
        return operatedClause;
    }

    private OperatedClause search(String indexId, List<TermsEntry> termsList, Type type, IndexSetting indexSetting) throws IOException {

        OperatedClause operatedClause = null;
		AtomicInteger termSequence = new AtomicInteger();
		
		int queryPosition = 0;

		for (TermsEntry entry : termsList) {

            List<String> terms = entry.getTerms();
            List<String> synonymTerms = entry.getSynonymTerms();

            OperatedClause termClause = search0(terms, type, indexId, queryPosition, termSequence);
            OperatedClause synonymClause = search0(synonymTerms, type, indexId, queryPosition, termSequence);
            queryPosition++;

            //유사어처리.
            if (synonymClause != null) {
                termClause = new OrOperatedClause(termClause, synonymClause);
            }

            if (operatedClause == null) {
                operatedClause = termClause;
            } else {
                if (type == Type.ALL) {
                    operatedClause = new AndOperatedClause(operatedClause, termClause);
                } else if (type == Type.ANY) {
                    operatedClause = new OrOperatedClause(operatedClause, termClause);
                }
            }

        }

		return operatedClause;
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
        writer.append(indentSpace).append("[ROOT]\n");
        if(operatedClause != null) {
            operatedClause.printTrace(writer, indent, depth + 1);
        }
    }

}
