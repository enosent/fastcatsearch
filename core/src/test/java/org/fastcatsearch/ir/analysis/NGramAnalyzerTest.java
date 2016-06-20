package org.fastcatsearch.ir.analysis;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.AnalyzerOption;
import org.apache.lucene.analysis.tokenattributes.CharsRefTermAttribute;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

public class NGramAnalyzerTest {

	@Test
	public void testTokenizer() throws IOException {
		int minGram = 2;
		int maxGram = 5;
		String str = "A Epic Tale of a Moose And a Girl who must Confront a Monkey in Ancient India";
		str = "대형폐가전제품무상방문 수거사업";
//		str = "사례에 대해서는 단언가 쪼개지지 않아서 그런 것 같다 라고 말씀 드리고,\n"
//				+"사용자 사전에 “가전”을 등록하면 검색이 가능해질거라고 말씀 드렸더니 \n"
//				+"매번 검색이 안될때마다 인식 하지 못한 단어를 등록할 수는 없다.. 라고 하시네요.";
		StringReader input = new StringReader(str);
		NGramWordTokenizer t = new NGramWordTokenizer(input, minGram, maxGram);
		t.reset();
		CharsRefTermAttribute charTermAttribute = t.getAttribute(CharsRefTermAttribute.class);
		int i = 1;
		while(t.incrementToken()) {
			System.out.println(i++ +">"+charTermAttribute.toString()+"<");
		}
	}

	@Test
	public void testAnalyzerForDocument() throws IOException, InterruptedException {
		String str = "대형폐가전제품무상방문 수거사업";
		NGramWordAnalyzer analyzer = new NGramWordAnalyzer();
		StringReader reader = new StringReader(str);
		TokenStream tokenStream = analyzer.tokenStream("1", reader);
		CharsRefTermAttribute charTermAttribute = tokenStream.getAttribute(CharsRefTermAttribute.class);
		
		int i = 1;
		while(tokenStream.incrementToken()) {
			System.out.println(i++ +">"+charTermAttribute.toString()+"<");
		}
		
		System.out.println("==============================================");
		
		str = "대형폐가전제품무상방문 수거사업";
		StringReader reader2 = new StringReader(str);
		
		TokenStream tokenStream2 = analyzer.tokenStream("2", reader2);
		CharsRefTermAttribute charTermAttribute2 = tokenStream2.getAttribute(CharsRefTermAttribute.class);
		
		int i2 = 1;
		while(tokenStream2.incrementToken()) {
			System.out.println(i2++ +">>>"+charTermAttribute2.toString()+"<");
		}
		
	}
	
	@Test
	public void testAnalyzerForQuery() throws IOException, InterruptedException {
		AnalyzerOption analyzerOption = new AnalyzerOption();
		analyzerOption.setForQuery();
		
		String str = "대형 폐가전 제품무상방문 수거사업";
		NGramWordAnalyzer analyzer = new NGramWordAnalyzer();
		StringReader reader = new StringReader(str);
		TokenStream tokenStream = analyzer.tokenStream("1", reader, analyzerOption);
		tokenStream.reset();
		
		CharsRefTermAttribute charTermAttribute = tokenStream.getAttribute(CharsRefTermAttribute.class);
		
		int i = 1;
		while(tokenStream.incrementToken()) {
			System.out.println(i++ +">"+charTermAttribute.toString()+"<");
		}
		
		System.out.println("==============================================");
		
		StringReader reader2 = new StringReader(str);
		
		TokenStream tokenStream2 = analyzer.tokenStream("2", reader2, analyzerOption);
		tokenStream2.reset();
		CharsRefTermAttribute charTermAttribute2 = tokenStream2.getAttribute(CharsRefTermAttribute.class);
		
		int i2 = 1;
		while(tokenStream2.incrementToken()) {
			System.out.println(i2++ +">>>"+charTermAttribute2.toString()+"<");
		}
		
	}

	@Test
	public void testTokenizerWithEdge() throws IOException {
		int minGram = 1;
		int maxGram = 3;
		boolean includeEdge = true;
		String str = "대형폐가전제품";
		StringReader input = new StringReader(str);
		NGramWordTokenizer t = new NGramWordTokenizer(input, minGram, maxGram, includeEdge);
		t.reset();
		CharsRefTermAttribute charTermAttribute = t.getAttribute(CharsRefTermAttribute.class);
		int i = 1;
		while(t.incrementToken()) {
			System.out.println(i++ +">"+charTermAttribute.toString()+"<");
		}
	}
}
