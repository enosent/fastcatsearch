package org.apache.lucene.search.highlight;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.AdditionalTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.util.PriorityQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Class used to markup highlighted terms found in the best sections of a text, using configurable {@link Fragmenter},
 * {@link Scorer}, {@link Formatter}, {@link Encoder} and tokenizers.
 */
public class Highlighter {
	private static Logger logger = LoggerFactory.getLogger(Highlighter.class);
	
	public static final int DEFAULT_MAX_CHARS_TO_ANALYZE = 50 * 1024;

	private int maxDocCharsToAnalyze = DEFAULT_MAX_CHARS_TO_ANALYZE;
	private Formatter formatter;
	private Encoder encoder;
	private Fragmenter textFragmenter = new SimpleFragmenter();
	private Scorer fragmentScorer = null;
	
	private Set<TermSorted> termSet;

	public Highlighter(Scorer fragmentScorer) {
		this(new SimpleHTMLFormatter(), fragmentScorer);
	}

	public Highlighter(Formatter formatter, Scorer fragmentScorer) {
		this(formatter, new DefaultEncoder(), fragmentScorer);
	}

	public Highlighter(Formatter formatter, Encoder encoder, Scorer fragmentScorer) {
		this.formatter = formatter;
		this.encoder = encoder;
		this.fragmentScorer = fragmentScorer;
		this.termSet = new TreeSet<TermSorted>();
	}

	/**
	 * Highlights chosen terms in a text, extracting the most relevant section. This is a convenience method that calls
	 * {@link #getBestFragment(TokenStream, String)}
	 * 
	 * @param analyzer
	 *            the analyzer that will be used to split <code>text</code> into chunks
	 * @param text
	 *            text to highlight terms in
	 * @param fieldName
	 *            Name of field used to influence analyzer's tokenization policy
	 * 
	 * @return highlighted text fragment or null if no terms found
	 * @throws InvalidTokenOffsetsException
	 *             thrown if any token's endOffset exceeds the provided text's length
	 */
	public final String getBestFragment(Analyzer analyzer, String fieldName, String text) throws IOException, InvalidTokenOffsetsException {
		TokenStream tokenStream = analyzer.tokenStream(fieldName, new StringReader(text));
		return getBestFragment(tokenStream, text);
	}

	/**
	 * Highlights chosen terms in a text, extracting the most relevant section. The document text is analysed in chunks to record
	 * hit statistics across the document. After accumulating stats, the fragment with the highest score is returned
	 * 
	 * @param tokenStream
	 *            a stream of tokens identified in the text parameter, including offset information. This is typically produced by
	 *            an analyzer re-parsing a document's text. Some work may be done on retrieving TokenStreams more efficiently by
	 *            adding support for storing original text position data in the Lucene index but this support is not currently
	 *            available (as of Lucene 1.4 rc2).
	 * @param text
	 *            text to highlight terms in
	 * 
	 * @return highlighted text fragment or null if no terms found
	 * @throws InvalidTokenOffsetsException
	 *             thrown if any token's endOffset exceeds the provided text's length
	 */
	public final String getBestFragment(TokenStream tokenStream, String text) throws IOException, InvalidTokenOffsetsException {
		String[] results = getBestFragments(tokenStream, text, 1);
		if (results.length > 0) {
			return results[0];
		}
		return null;
	}

	/**
	 * Highlights chosen terms in a text, extracting the most relevant sections. This is a convenience method that calls
	 * {@link #getBestFragments(TokenStream, String, int)}
	 * 
	 * @param analyzer
	 *            the analyzer that will be used to split <code>text</code> into chunks
	 * @param fieldName
	 *            the name of the field being highlighted (used by analyzer)
	 * @param text
	 *            text to highlight terms in
	 * @param maxNumFragments
	 *            the maximum number of fragments.
	 * 
	 * @return highlighted text fragments (between 0 and maxNumFragments number of fragments)
	 * @throws InvalidTokenOffsetsException
	 *             thrown if any token's endOffset exceeds the provided text's length
	 */
	public final String[] getBestFragments(Analyzer analyzer, String fieldName, String text, int maxNumFragments) throws IOException,
			InvalidTokenOffsetsException {
		TokenStream tokenStream = analyzer.tokenStream(fieldName, new StringReader(text));
		return getBestFragments(tokenStream, text, maxNumFragments);
	}

	/**
	 * Highlights chosen terms in a text, extracting the most relevant sections. The document text is analysed in chunks to record
	 * hit statistics across the document. After accumulating stats, the fragments with the highest scores are returned as an
	 * array of strings in order of score (contiguous fragments are merged into one in their original order to improve
	 * readability)
	 * 
	 * @param text
	 *            text to highlight terms in
	 * @param maxNumFragments
	 *            the maximum number of fragments.
	 * 
	 * @return highlighted text fragments (between 0 and maxNumFragments number of fragments)
	 * @throws InvalidTokenOffsetsException
	 *             thrown if any token's endOffset exceeds the provided text's length
	 */
	public final String[] getBestFragments(TokenStream tokenStream, String text, int maxNumFragments) throws IOException,
			InvalidTokenOffsetsException {
		maxNumFragments = Math.max(1, maxNumFragments); // sanity check

		TextFragment[] frag = getBestTextFragments(tokenStream, text, true, maxNumFragments);

		// Get text
		ArrayList<String> fragTexts = new ArrayList<String>();
		for (int i = 0; i < frag.length; i++) {
			if ((frag[i] != null) && (frag[i].getScore() > 0)) {
				fragTexts.add(frag[i].toString());
			}
		}
		return fragTexts.toArray(new String[0]);
	}

	/**
	 * Low level api to get the most relevant (formatted) sections of the document. This method has been made public to allow
	 * visibility of score information held in TextFragment objects. Thanks to Jason Calabrese for help in redefining the
	 * interface.
	 * 
	 * @throws IOException
	 *             If there is a low-level I/O error
	 * @throws InvalidTokenOffsetsException
	 *             thrown if any token's endOffset exceeds the provided text's length
	 */
	public final TextFragment[] getBestTextFragments(TokenStream tokenStream, String text, boolean mergeContiguousFragments, int maxNumFragments)
			throws IOException, InvalidTokenOffsetsException {
		ArrayList<TextFragment> docFrags = new ArrayList<TextFragment>();
		StringBuilder newText = new StringBuilder();

		CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
		OffsetAttribute offsetAtt = tokenStream.addAttribute(OffsetAttribute.class);
		AdditionalTermAttribute addAtt = tokenStream.addAttribute(AdditionalTermAttribute.class);
		tokenStream.reset();
		TextFragment currentFrag = new TextFragment(newText, newText.length(), docFrags.size());

		TokenStream newStream = fragmentScorer.init(tokenStream);
		if (newStream != null) {
			tokenStream = newStream;
		}
		fragmentScorer.startFragment(currentFrag);
		docFrags.add(currentFrag);

		FragmentQueue fragQueue = new FragmentQueue(maxNumFragments);

		try {

			String[] tokenText = new String[1];
			int[] startOffset = new int[1];
			int[] endOffset = new int[1];
			int[] lastEndOffset = new int[] { 0 };
			textFragmenter.start(text, tokenStream);

			TokenGroup tokenGroup = new TokenGroup(tokenStream);

			for (boolean next = tokenStream.incrementToken(); next && (offsetAtt.startOffset() < maxDocCharsToAnalyze); next = tokenStream
					.incrementToken()) {
				
				logger.trace("termAtt : {} [{}~{}]", termAtt, offsetAtt.startOffset(), offsetAtt.endOffset());
				
				if(tokenGroup.isDistinct()) {
					currentFrag = markUp(offsetAtt, termAtt, tokenGroup, text, tokenText,
						startOffset, endOffset, lastEndOffset, newText,
						docFrags, currentFrag, true);
				}
				tokenGroup.addToken(fragmentScorer.getTokenScore());
				
				//for additional-terms
				if(addAtt != null && addAtt.size() > 0) {
					Iterator<String> iter = addAtt.iterateAdditionalTerms();
					while(iter.hasNext()) {
						String str = iter.next();
						if(tokenGroup.isDistinct()) {
							currentFrag = markUp(offsetAtt, str, tokenGroup, text, tokenText,
								startOffset, endOffset, lastEndOffset, newText,
								docFrags, currentFrag, true);
						}
					}
				}
			}
			currentFrag.setScore(fragmentScorer.getFragmentScore());
			markUp(offsetAtt, termAtt, tokenGroup, text, tokenText,
				startOffset, endOffset, lastEndOffset, newText, 
				docFrags, currentFrag, false);

			// Test what remains of the original text beyond the point where we stopped analyzing
			if (
			// if there is text beyond the last token considered..
			(lastEndOffset[0] < text.length()) &&
			// and that text is not too large...
					(text.length() <= maxDocCharsToAnalyze)) {
				// append it to the last fragment
				String tmpStr = encoder.encodeText(text.substring(lastEndOffset[0]));
				newText.append(tmpStr);
				termSet.add(new TermSorted(tmpStr, null, lastEndOffset[0], text.length() - 1));
			}
			
			newText.setLength(0);
			newText.append(mergeTerms(termSet, tokenGroup));

			currentFrag.textEndPos = newText.length();

			// sort the most relevant sections of the text
			for (Iterator<TextFragment> i = docFrags.iterator(); i.hasNext();) {
				currentFrag = i.next();

				// If you are running with a version of Lucene before 11th Sept 03
				// you do not have PriorityQueue.insert() - so uncomment the code below
				/*
				 * if (currentFrag.getScore() >= minScore) { fragQueue.put(currentFrag); if (fragQueue.size() > maxNumFragments) {
				 * // if hit queue overfull fragQueue.pop(); // remove lowest in hit queue minScore = ((TextFragment)
				 * fragQueue.top()).getScore(); // reset minScore }
				 * 
				 * 
				 * }
				 */
				// The above code caused a problem as a result of Christoph Goller's 11th Sept 03
				// fix to PriorityQueue. The correct method to use here is the new "insert" method
				// USE ABOVE CODE IF THIS DOES NOT COMPILE!
				fragQueue.insertWithOverflow(currentFrag);
			}

			// return the most relevant fragments
			TextFragment frag[] = new TextFragment[fragQueue.size()];
			for (int i = frag.length - 1; i >= 0; i--) {
				frag[i] = fragQueue.pop();
			}

			// merge any contiguous fragments to improve readability
			if (mergeContiguousFragments) {
				mergeContiguousFragments(frag);
				ArrayList<TextFragment> fragTexts = new ArrayList<TextFragment>();
				for (int i = 0; i < frag.length; i++) {
					if ((frag[i] != null) && (frag[i].getScore() > 0)) {
						fragTexts.add(frag[i]);
					}
				}
				frag = fragTexts.toArray(new TextFragment[0]);
			}

			return frag;

		} finally {
			if (tokenStream != null) {
				try {
					tokenStream.end();
					tokenStream.close();
				} catch (Exception e) {
				}
			}
		}
	}

	public TextFragment markUp(OffsetAttribute offsetAtt, Object termAtt,
			TokenGroup tokenGroup, String text, String[] tokenText,
			int[] startOffset, int[] endOffset, int[] lastEndOffset,
			StringBuilder newText,
			ArrayList<TextFragment> docFrags, TextFragment currentFrag, boolean isDistinct)
			throws InvalidTokenOffsetsException {
		
		logger.trace("text:{}", termAtt);
		
		if ((offsetAtt.endOffset() > text.length()) || (offsetAtt.startOffset() > text.length())) {
			throw new InvalidTokenOffsetsException("Token " + termAtt.toString() + " exceeds length of provided text sized " + text.length());
		}
		logger.trace("numTokens:{} / distinct:{}", tokenGroup.numTokens, tokenGroup.isDistinct());
		if (tokenGroup.numTokens > 0) {
			// the current token is distinct from previous tokens -
			// markup the cached token group info
			startOffset[0] = tokenGroup.matchStartOffset;
			endOffset[0] = tokenGroup.matchEndOffset;
			tokenText[0] = text.substring(startOffset[0], endOffset[0]);
			
			String markedUpText = formatter.highlightTerm(encoder.encodeText(tokenText[0]), tokenGroup);
			
			logger.trace("text:{} / newText:{} / token:{} / markedUp:{} / startOffset:{} / lastEndOffset:{}", text, newText, tokenText, markedUpText, startOffset, lastEndOffset);

			if (startOffset[0] > lastEndOffset[0]) {
				newText.append(encoder.encodeText(text.substring(lastEndOffset[0], startOffset[0])));
				termSet.add(new TermSorted(encoder.encodeText(text.substring(lastEndOffset[0], startOffset[0])), null, lastEndOffset[0], startOffset[0]));
			}
			termSet.add(new TermSorted(tokenText[0], markedUpText, startOffset[0], endOffset[0]));

			logger.trace("TERMSET:{}", termSet);

			newText.append(markedUpText);
			lastEndOffset[0] = Math.max(endOffset[0], lastEndOffset[0]);
			
			logger.trace("newText:{}", newText);
			
			if(isDistinct) {
				tokenGroup.clear();
				// check if current token marks the start of a new fragment
				if (textFragmenter.isNewFragment()) {
					currentFrag.setScore(fragmentScorer.getFragmentScore());
					// record stats for a new fragment
					currentFrag.textEndPos = newText.length();
					currentFrag = new TextFragment(newText, newText.length(), docFrags.size());
					fragmentScorer.startFragment(currentFrag);
					docFrags.add(currentFrag);
				}
			}
		}
		return currentFrag;
	}

	/**
	 * Improves readability of a score-sorted list of TextFragments by merging any fragments that were contiguous in the original
	 * text into one larger fragment with the correct order. This will leave a "null" in the array entry for the lesser scored
	 * fragment.
	 * 
	 * @param frag
	 *            An array of document fragments in descending score
	 */
	private void mergeContiguousFragments(TextFragment[] frag) {
		boolean mergingStillBeingDone;
		if (frag.length > 1)
			do {
				mergingStillBeingDone = false; // initialise loop control flag
				// for each fragment, scan other frags looking for contiguous blocks
				for (int i = 0; i < frag.length; i++) {
					if (frag[i] == null) {
						continue;
					}
					// merge any contiguous blocks
					for (int x = 0; x < frag.length; x++) {
						if (frag[x] == null) {
							continue;
						}
						if (frag[i] == null) {
							break;
						}
						TextFragment frag1 = null;
						TextFragment frag2 = null;
						int frag1Num = 0;
						int frag2Num = 0;
						int bestScoringFragNum;
						int worstScoringFragNum;
						// if blocks are contiguous....
						if (frag[i].follows(frag[x])) {
							frag1 = frag[x];
							frag1Num = x;
							frag2 = frag[i];
							frag2Num = i;
						} else if (frag[x].follows(frag[i])) {
							frag1 = frag[i];
							frag1Num = i;
							frag2 = frag[x];
							frag2Num = x;
						}
						// merging required..
						if (frag1 != null) {
							if (frag1.getScore() > frag2.getScore()) {
								bestScoringFragNum = frag1Num;
								worstScoringFragNum = frag2Num;
							} else {
								bestScoringFragNum = frag2Num;
								worstScoringFragNum = frag1Num;
							}
							frag1.merge(frag2);
							frag[worstScoringFragNum] = null;
							mergingStillBeingDone = true;
							frag[bestScoringFragNum] = frag1;
						}
					}
				}
			} while (mergingStillBeingDone);
	}

	/**
	 * Highlights terms in the text , extracting the most relevant sections and concatenating the chosen fragments with a
	 * separator (typically "..."). The document text is analysed in chunks to record hit statistics across the document. After
	 * accumulating stats, the fragments with the highest scores are returned in order as "separator" delimited strings.
	 * 
	 * @param text
	 *            text to highlight terms in
	 * @param maxNumFragments
	 *            the maximum number of fragments.
	 * @param separator
	 *            the separator used to intersperse the document fragments (typically "...")
	 * 
	 * @return highlighted text
	 * @throws InvalidTokenOffsetsException
	 *             thrown if any token's endOffset exceeds the provided text's length
	 */
	public final String getBestFragments(TokenStream tokenStream, String text, int maxNumFragments, String separator) throws IOException,
			InvalidTokenOffsetsException {
		String sections[] = getBestFragments(tokenStream, text, maxNumFragments);
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < sections.length; i++) {
			if (i > 0) {
				result.append(separator);
			}
			result.append(sections[i]);
		}
		return result.toString();
	}

	public int getMaxDocCharsToAnalyze() {
		return maxDocCharsToAnalyze;
	}

	public void setMaxDocCharsToAnalyze(int maxDocCharsToAnalyze) {
		this.maxDocCharsToAnalyze = maxDocCharsToAnalyze;
	}

	public Fragmenter getTextFragmenter() {
		return textFragmenter;
	}

	public void setTextFragmenter(Fragmenter fragmenter) {
		textFragmenter = fragmenter;
	}

	/**
	 * @return Object used to score each text fragment
	 */
	public Scorer getFragmentScorer() {
		return fragmentScorer;
	}

	public void setFragmentScorer(Scorer scorer) {
		fragmentScorer = scorer;
	}

	public Encoder getEncoder() {
		return encoder;
	}

	public void setEncoder(Encoder encoder) {
		this.encoder = encoder;
	}

	public String mergeTerms(Set<TermSorted> termSet, TokenGroup tokenGroup) {
		StringBuilder ret = new StringBuilder();
		//trimming...
		TermSorted prev = null;
		while(true) {
			TermSorted item = null;
			for(TermSorted term: termSet) {
				item = null;
				if(prev != null && prev.edOffset() >= term.stOffset()) {
					String termStr = null;
					int delta = prev.edOffset() - term.stOffset();
					if(delta > 0) {
						//nested string..
						if (prev.tagTerm() == null) {
							termStr = prev.orgTerm();
							//cut by different
							if (termStr.length() >= delta) {
								prev.orgTerm(termStr.substring(0, termStr.length() - delta))
									.edOffset(term.stOffset());
							}
							//
							if (prev.edOffset() > term.edOffset()) {
								item = new TermSorted(termStr.substring(term.edOffset()), null, term.edOffset(), prev.edOffset());
								break;
							}
						} else {
							//cut current term when previous term is highlighted
							if (prev.edOffset() > term.edOffset()) {
								//when previous term contains whole current term
								//current term will useless
								term.orgTerm("").tagTerm(null).edOffset(term.stOffset());
							} else {
								//when nested string (previous term/ current term)
								//cut current term
								termStr = term.orgTerm();
								termStr = termStr.substring(delta);
								if (term.tagTerm() != null) {
									term.tagTerm(formatter.highlightTerm(termStr, tokenGroup));
								}
								term.stOffset(prev.edOffset());
							}
						}
					}
				}
				//deleted term..
				if(!"".equals(term.orgTerm())) {
					prev = term;
				}
			}
			if(item == null) {
				break;
			}
		}
		//merging...
		for(TermSorted term: termSet) {
			if(term.tagTerm()==null) {
				ret.append(term.orgTerm());
			} else {
				ret.append(term.tagTerm());
			}
		}
		return ret.toString();
	}
}

class FragmentQueue extends PriorityQueue<TextFragment> {
	public FragmentQueue(int size) {
		super(size);
	}

	@Override
	public final boolean lessThan(TextFragment fragA, TextFragment fragB) {
		if (fragA.getScore() == fragB.getScore())
			return fragA.fragNum > fragB.fragNum;
		else
			return fragA.getScore() < fragB.getScore();
	}
}


class TermSorted implements Comparable<TermSorted> {
	private String orgTerm;
	private String tagTerm;
	private int stOffset;
	private int edOffset;

	public TermSorted(String orgTerm, String tagTerm, int stOffset, int edOffset) {
		this.orgTerm = orgTerm;
		this.tagTerm = null;
		if(tagTerm!=null && !tagTerm.equals(orgTerm)) {
			this.tagTerm = tagTerm;
		}
		this.stOffset = stOffset;
		this.edOffset = edOffset;
	}
	
	@Override
	public int compareTo(TermSorted o) {
		int ret = -1;
		
		if ( o != null ) {
			
			ret = stOffset - o.stOffset;
			
			if ( ret == 0 ) { ret = o.edOffset - edOffset; }
			
			if ( ret == 0 ) { ret = orgTerm.compareTo(o.orgTerm); }
			
			if ( ret > 0 ) { 
				ret = 1;
			} else if ( ret < 0 ) {
				ret = -1;
			}
		}
		return ret;
	}

	public String orgTerm() { return orgTerm; }
	public TermSorted orgTerm(String orgTerm) { this.orgTerm = orgTerm; return this; }
	public String tagTerm() { return tagTerm; }
	public TermSorted tagTerm(String tagTerm) { this.tagTerm = tagTerm; return this; }
	public int stOffset() { return stOffset; }
	public TermSorted stOffset(int stOffset) { this.stOffset = stOffset; return this; }
	public int edOffset() { return edOffset; }
	public TermSorted edOffset(int edOffset) { this.edOffset = edOffset; return this; }
	@Override public String toString() { return (tagTerm!=null?tagTerm:orgTerm)+":"+stOffset+"~"+edOffset; }
}