package org.fastcatsearch.ir.analysis;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.fastcatsearch.util.lang.ko.HangulUtil;

import java.io.IOException;
import java.io.Reader;

/**
 * Created by swsong on 2015. 7. 19..
 */
public class AutocompleteTokenizer extends Tokenizer {

    private char[] readBuffer;
    private String[] tokenList;
    private int pos;
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    public AutocompleteTokenizer(Reader input) {
        super(input);
        readBuffer = new char[1024];
    }

    @Override
    public void reset() throws IOException {

        StringBuffer sb = new StringBuffer();
        int n = 0;
        while ( (n = input.read(readBuffer)) >= 0) {
            sb.append(readBuffer, 0, n);
        }

        String input = sb.toString();
        char delimiter = ' ';
        String delimiterStr = String.valueOf(delimiter);

        String[] keywordArray = input.split("[ \t\n\r]");
        StringBuilder output = new StringBuilder();
        for(String keyword : keywordArray) {
            output.append(HangulUtil.makeHangulPrefix(keyword, delimiter)).append(delimiter);
            String chosung = HangulUtil.makeHangulChosung(keyword, delimiter);
            if(chosung.length() > 0) {
                output.append(HangulUtil.makeHangulChosung(keyword, delimiter)).append(delimiter);
            }
        }
        tokenList = output.toString().split(delimiterStr);
        pos = 0;
    }

    @Override
    public boolean incrementToken() throws IOException {
        clearAttributes();
        if(pos < tokenList.length) {
            char[] buf = termAtt.buffer();

            String token = tokenList[pos];
            if(buf.length < token.length()) {
                buf = termAtt.resizeBuffer(token.length());
            }
            for(int i = 0; i< token.length(); i++) {
                buf[i] = token.charAt(i);
            }

            termAtt.setLength(token.length());
            pos++;
            return true;
        }

        return false;
    }
}
