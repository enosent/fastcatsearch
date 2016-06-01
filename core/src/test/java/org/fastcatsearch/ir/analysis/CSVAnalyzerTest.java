package org.fastcatsearch.ir.analysis;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.CSVAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

public class CSVAnalyzerTest {
	
	private static final Logger logger = LoggerFactory.getLogger(CSVAnalyzerTest.class);

	@Before
	public void init() {
		String LOG_LEVEL = System.getProperty("LOG_LEVEL");
		
		if(LOG_LEVEL==null || "".equals(LOG_LEVEL)) {
			LOG_LEVEL = "DEBUG";
		}
		
		((ch.qos.logback.classic.Logger)
			LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
			).setLevel(Level.toLevel("DEBUG"));
		
		((ch.qos.logback.classic.Logger)
			LoggerFactory.getLogger(TokenStream.class)
			).setLevel(Level.toLevel(LOG_LEVEL));
	}
	
	@Test
	public void testBulk() throws IOException {
		String str = "";
		str = "SK,  하이닉스";
		//str = "하이닉스";
		
		StringReader input = new StringReader(str);
		CSVAnalyzer analyzer = new CSVAnalyzer();
		TokenStream tokenStream = analyzer.tokenStream("", input);
		tokenStream.reset();
		logger.debug("tokenStream:{}", tokenStream);
		CharTermAttribute charTermAttribute = tokenStream.getAttribute(CharTermAttribute.class);
		OffsetAttribute offsetAttribute = tokenStream.getAttribute(OffsetAttribute.class);
		for(int inx=0;tokenStream.incrementToken();inx++) {
			String term = charTermAttribute.toString();
			logger.debug("[{}] \"{}\" {}~{}", inx, term, offsetAttribute.startOffset(), offsetAttribute.endOffset());
		}
		analyzer.close();
	}
	
	@Test
	public void testTokenizer() throws IOException {
		
		String str = "";
		str = "  하이닉스,abc,123 ,456, 789, 1011 , 한글 ,english,漢文,日本語, SK ,, ,하이닉스 , ";
		
		String[] resultSet =  {
			"하이닉스", 
			"abc",
			"123",
			"456",
			"789",
			"1011",
			"한글",
			"english",
			"漢文",
			"日本語",
			"SK",
			"하이닉스"
		};
		
		StringReader input = new StringReader(str);
		CSVAnalyzer analyzer = new CSVAnalyzer();
		TokenStream tokenStream = analyzer.tokenStream("", input);
		tokenStream.reset();
		logger.debug("tokenStream:{}", tokenStream);
		CharTermAttribute charTermAttribute = tokenStream.getAttribute(CharTermAttribute.class);
		OffsetAttribute offsetAttribute = tokenStream.getAttribute(OffsetAttribute.class);
		for(int inx=0;tokenStream.incrementToken();inx++) {
			String term = charTermAttribute.toString();
			logger.debug("[{}] \"{}\" {}~{}", inx, term, offsetAttribute.startOffset(), offsetAttribute.endOffset());
			assertEquals(resultSet[inx], term);
		}
		analyzer.close();
	}

    @Test
    public void testMultiple() throws IOException {
        String str1 = "SK,  하이닉스,";
        String str2 = "LG,  GS,";
        //str = "하이닉스";

        CSVAnalyzer analyzer = new CSVAnalyzer();

        TokenStream tokenStream = analyzer.tokenStream("", new StringReader(str1));
        tokenStream.reset();
        logger.debug("tokenStream:{}", tokenStream);
        CharTermAttribute charTermAttribute = tokenStream.getAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAttribute = tokenStream.getAttribute(OffsetAttribute.class);
        for(int inx=0;tokenStream.incrementToken();inx++) {
            String term = charTermAttribute.toString();
            logger.debug("[{}] \"{}\" {}~{}", inx, term, offsetAttribute.startOffset(), offsetAttribute.endOffset());
        }

        TokenStream tokenStream2 = analyzer.tokenStream("", new StringReader(str2));
        tokenStream2.reset();
        logger.debug("tokenStream:{}", tokenStream2);
        CharTermAttribute charTermAttribute2 = tokenStream2.getAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAttribute2 = tokenStream2.getAttribute(OffsetAttribute.class);
        for(int inx=0;tokenStream2.incrementToken();inx++) {
            String term = charTermAttribute2.toString();
            logger.debug("[{}] \"{}\" {}~{}", inx, term, offsetAttribute2.startOffset(), offsetAttribute2.endOffset());
        }

        analyzer.close();
    }
}
