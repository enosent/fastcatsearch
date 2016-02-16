package org.fastcatsearch.http.action.service;

import org.junit.Test;

/**
 * Created by swsong on 2016. 2. 16..
 */
public class DemoSearchTest {

    @Test
    public void testEscape() {

        String keyword = "충전:데이터 브라&가터 (2종) 베비돌&쇼츠;2~3일 배송 [mju:] 로지==텍 1,2,3,4";

        System.out.println(keyword);
        System.out.println(escape(keyword));

    }
    private String escape(String keyword) {
        keyword = keyword.replaceAll("(?<!\\\\)&", "\\\\&");
        keyword = keyword.replaceAll("(?<!\\\\)=", "\\\\=");
        keyword = keyword.replaceAll("(?<!\\\\),", "\\\\,");
        keyword = keyword.replaceAll("(?<!\\\\)\\(", "\\\\(");
        keyword = keyword.replaceAll("(?<!\\\\)\\)", "\\\\)");
        keyword = keyword.replaceAll("(?<!\\\\)\\{", "\\\\{");
        keyword = keyword.replaceAll("(?<!\\\\)\\}", "\\\\}");
        keyword = keyword.replaceAll("(?<!\\\\):", "\\\\:");
        keyword = keyword.replaceAll("(?<!\\\\);", "\\\\;");
        keyword = keyword.replaceAll("(?<!\\\\)~", "\\\\~");
        return keyword;
    }
}
