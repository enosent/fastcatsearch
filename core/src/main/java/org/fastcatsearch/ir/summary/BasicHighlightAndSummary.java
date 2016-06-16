package org.fastcatsearch.ir.summary;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.AnalyzerOption;
import org.apache.lucene.analysis.tokenattributes.AdditionalTermAttribute;
import org.apache.lucene.analysis.tokenattributes.AdditionalTermAttributeImpl;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharsRefTermAttribute;
import org.apache.lucene.analysis.tokenattributes.FeatureAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.StopwordAttribute;
import org.apache.lucene.analysis.tokenattributes.SynonymAttribute;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.Scorer;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.WeightedTerm;
import org.apache.lucene.util.CharsRef;
import org.fastcatsearch.ir.io.CharVector;
import org.fastcatsearch.ir.query.Term.Option;
import org.fastcatsearch.ir.search.HighlightAndSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicHighlightAndSummary implements HighlightAndSummary {

	private static final Logger logger = LoggerFactory.getLogger(BasicHighlightAndSummary.class);
	
	private static final String FRAGMENT_SEPARATOR = "...";

	
	@Override
	public String highlight(String fieldId, Analyzer indexAnalyzer, Analyzer queryAnalyzer, String pText, String query, 
			String[] tags, int len, int maxFragments, Option searchOption) throws IOException {

		//
		// initialize tags ( if null or blank )
		//
		if(tags==null) {
			tags = new String[]{"",""};
		}
		
		for(int inx=0;inx<tags.length;inx++) {
			if(tags[inx]==null) { tags[inx]=""; }
		}

		//
		// minimum count of fragments
		//
		if(maxFragments <= 0) {
			maxFragments = 1;
		}

		//
		// one full length of summary 
		//
		if(len<=0) {
			len = pText.length() + 1;
		}
		
		//
		// lucene summary length is size of each fragment, so divide it by count of fragments
		//
		len = len / maxFragments;
		
		TokenStream tokenStream = null;
		
		
		
		//TODO 스니펫만 만들고 하이라이팅을 하지 않는필드에 대해서는 DummyFormatter를 만들어서 넣어준다.
		
		
		Formatter formatter = new SimpleHTMLFormatter(tags[0], tags[1]);
		
		logger.trace("query : {}", query);
		//
		// tokenize query and make weighted terms
		//
		List<WeightedTerm> terms = new ArrayList<WeightedTerm>();
		
		AnalyzerOption queryAnalyzerOption = new AnalyzerOption();
		queryAnalyzerOption.useStopword(searchOption.useStopword());
		queryAnalyzerOption.useSynonym(searchOption.useSynonym());
		queryAnalyzerOption.setForQuery();
		//queryAnalyzerOption.setForDocument();
		
		tokenStream = queryAnalyzer.tokenStream(fieldId, new StringReader(query), queryAnalyzerOption);
		
		CharsRefTermAttribute termAttribute = null;
		CharTermAttribute charTermAttribute = null;
		if(tokenStream.hasAttribute(CharsRefTermAttribute.class)){
			termAttribute = tokenStream.getAttribute(CharsRefTermAttribute.class);
		}
		if(tokenStream.hasAttribute(CharTermAttribute.class)) {
			charTermAttribute = tokenStream.getAttribute(CharTermAttribute.class);
		}
		
		FeatureAttribute featureAttribute = null;
		StopwordAttribute stopwordAttribute = null;
		OffsetAttribute offsetAttribute = null;
		
		if(tokenStream.hasAttribute(FeatureAttribute.class)) {
			featureAttribute = tokenStream.getAttribute(FeatureAttribute.class);
		}
		if(tokenStream.hasAttribute(StopwordAttribute.class)) {
			stopwordAttribute = tokenStream.getAttribute(StopwordAttribute.class);
		}
		if(tokenStream.hasAttribute(OffsetAttribute.class)) {
			offsetAttribute = tokenStream.getAttribute(OffsetAttribute.class);
		}
		tokenStream.reset();
		
		String prevTermString = null;
		while(tokenStream.incrementToken()) {
			
			//불용어면 하이라이팅에서 제외.
			if(stopwordAttribute != null){
				if(stopwordAttribute.isStopword()){
					continue;
				}
			}
			
			String termString = null;
			if(charTermAttribute != null) {
				termString = new String(charTermAttribute.buffer(), 0, charTermAttribute.length());
			}
			
			if (termString == null || "".equals(termString)
					&& termAttribute != null) {
				termString = termAttribute.charsRef().toString();
			}

            if(offsetAttribute != null) {
                logger.trace("termString:{} [{}~{}]", termString, offsetAttribute.startOffset(), offsetAttribute.endOffset());
            }
			float score = 0f;
			
			if(featureAttribute!=null) {
				if(featureAttribute.type()==FeatureAttribute.FeatureType.MAIN) {
					logger.trace("MAIN WORD {}", termString);
					score = 3.0f;
				} else if(featureAttribute.type()==FeatureAttribute.FeatureType.ADDITION) {
					logger.trace("ADDITION WORD {}", termString);
					score = 1.0f;
				} else if(featureAttribute.type()==FeatureAttribute.FeatureType.APPEND) {
					logger.trace("APPEND WORD {}", termString);
					score = 0f;
				} else if(featureAttribute.type()==FeatureAttribute.FeatureType.NULL) {
					logger.trace("NULL {}", termString);
					score = 0f;
				}
			} else {
				score = 1.0f;
			}
			
			logger.trace("termString:{} / score:{} / prev:{}", termString, score, prevTermString);
			
			if(score > 0) {
				if(!termString.equals(prevTermString)){
					terms.add(new WeightedTerm(score, termString));
					logger.trace("++ charTermAttribute : {}", termString);
				}
				prevTermString = termString;
				
				//refterm 은 1글자 이하의 텀에서는 의미가 없음
				if(termString.length() > 1) {
					if(logger.isTraceEnabled() && offsetAttribute != null) {
						logger.trace("charTermAttribute : {} : [{}/{}]", termString, 
								offsetAttribute.startOffset(), termAttribute.charsRef().length());
					}
					
					if (termAttribute != null) {
						CharsRef charRef = termAttribute.charsRef();
						if (charRef!=null && !(charRef.offset > 0 && charRef.length() == 1)) {
							terms.add(new WeightedTerm(score, charRef.toString()));
							logger.trace("++ charRefTerm : {}", termString);
						}
					}
				}
			}
		}
		
		SynonymAttribute synonymAttribute = null;
		if(tokenStream.hasAttribute(SynonymAttribute.class)) {
			synonymAttribute = tokenStream.getAttribute(SynonymAttribute.class);
		}

		if(synonymAttribute != null) {
			List<Object> synonymObj = synonymAttribute.getSynonyms();
			if(synonymObj !=null) {
				float score = 1.0f;
				for(Object obj : synonymObj) {
					if(obj instanceof CharVector) {
						String termString = obj.toString();
						if(!termString.equals(prevTermString)){
							terms.add(new WeightedTerm(score, termString));
							logger.trace("++ charTermAttribute : {}", termString);
						}
						prevTermString = termString;
					} else if (obj instanceof List) {
						@SuppressWarnings("unchecked")
						List<CharVector> synonyms = (List<CharVector>)obj;
						for(CharVector cv : synonyms) {
							String termString = cv.toString();
							if(!termString.equals(prevTermString)){
								terms.add(new WeightedTerm(score, termString));
								logger.trace("++ charTermAttribute : {}", termString);
							}
							prevTermString = termString;
						}
					}
				}
			}
		}
		
		WeightedTerm[] weightedTerms = new WeightedTerm[terms.size()];
		weightedTerms = terms.toArray(weightedTerms);
		
		Scorer scorer = new TokenizedTermScorer(weightedTerms);
		Highlighter highlighter = new Highlighter(formatter, scorer);
		Fragmenter fragmenter = new SimpleFragmenter(len);
		highlighter.setTextFragmenter(fragmenter);
		
		AnalyzerOption indexAnalyzerOption = new AnalyzerOption();
		indexAnalyzerOption.useStopword();
		indexAnalyzerOption.useSynonym(false);
		//indexAnalyzerOption.setForQuery();
		indexAnalyzerOption.setForDocument();
		
		tokenStream = new WrappedTokenStream(indexAnalyzer.tokenStream(fieldId, new StringReader(pText), indexAnalyzerOption), pText);
		
		String text = pText;

		try {
			text = highlighter.getBestFragments(tokenStream, pText, maxFragments, FRAGMENT_SEPARATOR);
		} catch (InvalidTokenOffsetsException e) {
			logger.debug("error : {} / pText:{} / query:{} / result:{}", e.getMessage(),  pText, query);
		}

		//
		// return original text when if not summarized
		//
		if (text == null || "".equals(text)) {
			if (len > pText.length()) {
				len = pText.length();
			}
			text = pText.substring(0, len);
		}
		logger.trace("highlighted:{}",text);
		return text;
	}
	
	
	class WrappedTokenStream extends TokenStream {
		
		private String pText;
		
		private TokenStream tokenStream;
		private OffsetAttribute offsetAttribute;
		private CharTermAttribute charTermAttribute;
		private CharsRefTermAttribute charsRefTermAttribute;
		private AdditionalTermAttribute additionalTermAttribute;
		
//		private OffsetAttribute offsetAttributeLocal;
//		private CharTermAttribute charTermAttributeLocal;
//		private CharsRefTermAttribute charsRefTermAttributeLocal;
//		private AdditionalTermAttribute additionalTermAttributeLocal;
//		private Iterator<String> additionalTerms;
		
		private OffsetAttribute offsetAttributeLocal = this.addAttribute(OffsetAttribute.class);
		private CharTermAttribute charTermAttributeLocal = this.addAttribute(CharTermAttribute.class);
		private CharsRefTermAttribute charsRefTermAttributeLocal = this.addAttribute(CharsRefTermAttribute.class);
		private AdditionalTermAttribute additionalTermAttributeLocal = this.addAttribute(AdditionalTermAttribute.class);

		public WrappedTokenStream(TokenStream tokenStream, String pText) {
			this.pText = pText;
			this.tokenStream = tokenStream;
			if(tokenStream.hasAttribute(CharTermAttribute.class)) {
				charTermAttribute = tokenStream.getAttribute(CharTermAttribute.class);
			}
			if(tokenStream.hasAttribute(OffsetAttribute.class)) {
				offsetAttribute = tokenStream.getAttribute(OffsetAttribute.class);
			}
			if(tokenStream.hasAttribute(CharsRefTermAttribute.class)) {
				charsRefTermAttribute = tokenStream.getAttribute(CharsRefTermAttribute.class);
			}
			
			if(tokenStream.hasAttribute(AdditionalTermAttribute.class)) {
				additionalTermAttribute = tokenStream.getAttribute(AdditionalTermAttribute.class);
			}
			
			additionalTermAttributeLocal.init(this);
		}
		@Override
		public void end() throws IOException { tokenStream.end(); }
		@Override
		public void reset() throws IOException { 
			this.tokenStream.reset(); 
			additionalTermAttributeLocal.init(this);
		}
		@Override
		public void close() throws IOException { tokenStream.close(); }
		@Override
		public boolean incrementToken() throws IOException {
			boolean ret = false;
			//CharTermAttribute나 offsetAttribute가 제공안되면 하이라이팅 불가. 
			if(offsetAttribute == null || charTermAttribute == null) {
				return false;
			}
						
			ret = tokenStream.incrementToken();
		
			char[] buffer;
			int offset;
			int length;
			
			if(charsRefTermAttribute!=null && charsRefTermAttribute.charsRef() != null) {
				buffer = charsRefTermAttribute.charsRef().chars;
				offset = charsRefTermAttribute.charsRef().offset;
				length = charsRefTermAttribute.charsRef().length;
				charsRefTermAttributeLocal.setBuffer(buffer, offset, length);
				charTermAttributeLocal.copyBuffer(buffer, offset, length);
				charTermAttributeLocal.setLength(length);
				
			} else {
				if(charTermAttributeLocal.buffer().length < charTermAttribute.buffer().length ) {
					charTermAttributeLocal.resizeBuffer(charTermAttribute.buffer().length);
				}
				buffer = charTermAttribute.buffer();
				length = charTermAttribute.length();
				charTermAttributeLocal.copyBuffer(buffer, 0, length);
				charTermAttributeLocal.setLength(length);
			}
			offsetAttributeLocal.setOffset(offsetAttribute.startOffset(), offsetAttribute.endOffset());
			if(logger.isTraceEnabled()) {
				logger.trace("text:{} / {}", charTermAttributeLocal, pText
						.substring(offsetAttributeLocal.startOffset(), offsetAttributeLocal.endOffset()));
			}
			//if( charTermAttributeLocal.length() > 0 
			//		&& offsetAttributeLocal.startOffset() + length <= pText.length()
			//		&& charTermAttributeLocal.buffer()[0] == 
			//			pText.charAt(offsetAttribute.startOffset())) {
			//	offsetAttributeLocal.setOffset(offsetAttribute.startOffset(),
			//			offsetAttribute.startOffset() + length);
			//} else {
				offsetAttributeLocal.setOffset(offsetAttribute.startOffset(),
						offsetAttribute.endOffset());
			//}
			
			if(additionalTermAttribute != null) {
				((AdditionalTermAttributeImpl)additionalTermAttribute).cloneTo((AdditionalTermAttributeImpl) additionalTermAttributeLocal);
			}
			return ret;
		}
		
	}
}
