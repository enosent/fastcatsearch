package org.fastcatsearch.datasource.reader;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by white on 2016-02-25.
 */
public class WebPageSourceReaderTest {

    @Test
    public void testTitleParsing() {
        String htmlText =
                "<title>\n" +
                "\t박 대통령, 주먹으로 책상치며&lt;br&gt; \"필리버스터? 기가 막힌 현상\" - 오마이뉴스\n" +
                "</title>";
        /*String htmlText = "<title>테스트</title>";*/
        String title = "";

        Pattern p = Pattern.compile("<title>\\n(.*)\\n</title>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(htmlText);

        if (m.find()) {
            title = m.group(1);
        } else {
            if (htmlText.length() > 10) {
                title = htmlText.substring(0,10);
            }else{
                title = htmlText;
            }
        }
    }
}
