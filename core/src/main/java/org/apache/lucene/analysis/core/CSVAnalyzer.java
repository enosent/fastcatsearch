package org.apache.lucene.analysis.core;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.	See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.	You may obtain a copy of the License at
 *
 *		 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

public final class CSVAnalyzer extends Analyzer {
	
	public CSVAnalyzer() {
	}
	
	@Override
	protected TokenStreamComponents createComponents(final String fieldName,
			final Reader reader) {
		return new TokenStreamComponents(new CSVTokenizer(reader));
	}
}

/*
 * 사용시 주의 : 컴마 뒤의 공백은 없어지나, 컴마 앞의 공백 즉, 단어 뒤에 공백이 있을시 사라지지 않음.		
 * */
final class CSVTokenizer extends Tokenizer {
	
	private static final char[] WHITESPACES = { ' ', '\t', '\n', '\r' };
	
	private char[] cbuf;
	private int currentPos;
	private int dataStarts;
	private int lastReaded;
	private int lastPos;
	private int baseOffset;
	private int nextOffset;
	
	private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);
	private final OffsetAttribute offsetAttribute = addAttribute(OffsetAttribute.class);

	public CSVTokenizer(Reader in) {
		super(in);
		cbuf = new char[4096];
		baseOffset = 0;
		nextOffset = 0;
		lastPos = -1;
		dataStarts = -1;
	}

	@Override
	public void setReader(Reader input) throws IOException {
		super.setReader(input);
		currentPos = 0;
		baseOffset = 0;
		nextOffset = 0;
		lastPos = -1;
		dataStarts = -1;
		this.clearAttributes();
	}
	
	private boolean isWhiteSpace(char c) {
		for (int inx = 0; inx < WHITESPACES.length; inx++) {
			if(WHITESPACES[inx] == c) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public boolean incrementToken() throws IOException {
	
		while(true) {
			logger.trace("current:{} / lastReaded:{} / dataStarts:{} / lastPos:{}", currentPos, lastReaded, dataStarts, lastPos);
			if(currentPos < lastReaded) {
				//read from buffer.
				if(dataStarts == -1) {
					if(lastPos == -1) {
						//ignore first blank.
						for (; currentPos < lastReaded;) {
							if (!isWhiteSpace(cbuf[currentPos])) {
								dataStarts = currentPos;
								lastPos = dataStarts;
								break;
							}
							currentPos++;
						}//for - removeWhitespace
					} else {
						dataStarts = 0;
					}
				}
					
				if(currentPos < lastReaded) {
					//comma detected. export char data
					if (cbuf[currentPos] == ',') {
						int length = 0;
						if (lastPos > dataStarts) {
							length = lastPos - dataStarts + 1;
							
							int startOffset = baseOffset + dataStarts;
							int endOffset = startOffset + length;
							
							termAttribute.copyBuffer(cbuf, dataStarts, length);
							offsetAttribute.setOffset(startOffset, endOffset);
							if(logger.isTraceEnabled()) {
								String term = new String(cbuf, dataStarts, length);
								logger.trace("output token:[{}] {}~{}", term, dataStarts, length);
							}
							lastPos = -1;
							dataStarts = -1;
							currentPos++;
							return true;
						} else {
							//init position
							dataStarts = -1;
							lastPos = -1;
						}
						
					} else {
						//for ignore whitespace at rear
						if (!isWhiteSpace(cbuf[currentPos])) {
							lastPos = currentPos;
						}
					}
					currentPos++;
				}
				//continue to first while
			} else {
				//if buffer not processed
				if(dataStarts != -1) {
					if(dataStarts < currentPos) {
						int length = currentPos - dataStarts;
						//shift data to head of array
						System.arraycopy(cbuf, dataStarts, cbuf, 0, length);
						dataStarts = 0;
						lastPos = length - (currentPos - lastPos);
						currentPos = length;
					}
				}
				
				if(currentPos == lastReaded) {
					currentPos = 0;
				}
				
				int rlen = 0;
				//read data from input
				rlen = input.read(cbuf, currentPos, cbuf.length - currentPos);
				
				if(logger.isTraceEnabled()) {
					logger.trace("read data..{} chars from {} / \"{}\"", rlen,
						currentPos, new String(cbuf, 0, currentPos + (rlen == -1 ? 0 : rlen)));
				}
				
				if(rlen == -1) {
					//no-more data
					break;
				}
				baseOffset = nextOffset;
				nextOffset += rlen;
				lastReaded = currentPos + rlen;

				/*
				* 2016-05-31 전제현
				* CSV 분석 시 분석 대상 텍스트 맨 뒤에 콤마가 존재할 시 해당 부분은 분석에서 제외한다.
				* */
				while (this.cbuf[lastReaded - 1] == ',') {
					lastReaded--;
				}
				continue;
			}
		}
		
		if(dataStarts != -1) {
			int length = lastPos - dataStarts + 1;
			int startOffset = baseOffset + dataStarts;
			int endOffset = startOffset + length;
			
			termAttribute.copyBuffer(cbuf, dataStarts, length);
			offsetAttribute.setOffset(startOffset, endOffset);
			if(logger.isTraceEnabled()) {
				String term = new String(cbuf, dataStarts, length);
				logger.trace("output token:[{}] {}~{}", term, dataStarts, length);
			}
			lastPos = -1;
			dataStarts = -1;
			lastReaded = -1;
			currentPos++;
			return true;
		}
		return false;
	}
}