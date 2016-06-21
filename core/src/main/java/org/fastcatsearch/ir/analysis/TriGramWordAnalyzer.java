package org.fastcatsearch.ir.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;

/**
 * 1-gram, 2-gram과 3-gram으로 뽑아낸다.
 * */
public class TriGramWordAnalyzer extends Analyzer {

	private static final Logger logger = LoggerFactory.getLogger(TriGramWordAnalyzer.class);

	public TriGramWordAnalyzer() {
	}

	@Override
	protected TokenStreamComponents createComponents(String fieldName, Reader reader) {

		final NGramWordTokenizer tokenizer = new NGramWordTokenizer(reader, 3, 3, true);

		TokenFilter filter = new StandardFilter(tokenizer);

		return new TokenStreamComponents(tokenizer, filter);
	}
}