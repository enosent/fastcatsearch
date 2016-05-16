package org.fastcatsearch.ir.analysis;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;

/**
 * Created by 전제현 on 2016-05-13.
 */
public class AutocompleteAnalyzerTest {

    private static final Logger logger = LoggerFactory.getLogger(AutocompleteAnalyzerTest.class);

    @Test
    public void testTokenizer() throws IOException {

        try {
            String str = "안녕하세용안녕하세용안녕하세용안ㄴ";
            StringReader input = new StringReader(str);
            AutocompleteTokenizer autocomplete = new AutocompleteTokenizer(input);
            autocomplete.reset();

            CharTermAttribute charTermAttribute = autocomplete.getAttribute(CharTermAttribute.class);

            int i = 1;
            while (autocomplete.incrementToken()) {
                System.out.println(i++ + ">" + charTermAttribute.toString() + "<");
                logger.debug("count: {}", i);
            }
        } catch (IOException e) {
            logger.error("IOException: ", e);
            throw e;
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.error("ArrayIndexOutOfBoundsException: " + e);
            throw e;
        }
    }
}
