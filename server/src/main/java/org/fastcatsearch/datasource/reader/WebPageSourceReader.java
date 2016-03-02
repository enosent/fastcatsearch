package org.fastcatsearch.datasource.reader;

import org.fastcatsearch.datasource.SourceModifier;
import org.fastcatsearch.datasource.reader.annotation.SourceReader;
import org.fastcatsearch.ir.common.IRException;
import org.fastcatsearch.ir.config.SingleSourceConfig;
import org.fastcatsearch.util.HTMLTagRemover;
import org.fastcatsearch.util.ReadabilityExtractor;
import org.fastcatsearch.util.WebPageGather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by 전제현 on 2016-02-22.
 * 설정에서 URL List를 읽어와 해당 URL의 내용을 파싱하여 색인한다. (웹페이지 색인)
 */
@SourceReader(name="WEBPAGE")
public class WebPageSourceReader extends SingleSourceReader<Map<String, Object>> {

    protected static Logger logger = LoggerFactory.getLogger(WebPageSourceReader.class);

    private String[] urlList;
    private Map<String, Object> dataMap;
    private Pattern p;
    private int lineNum;
    private WebPageGather webPageGather;
    private static ReadabilityExtractor extractor = new ReadabilityExtractor();


    public WebPageSourceReader() {
        super();
    }

    public WebPageSourceReader(String collectionId, File filePath, SingleSourceConfig singleSourceConfig, SourceModifier<Map<String, Object>> sourceModifier, String lastIndexTime) throws IRException {
        super(collectionId, filePath, singleSourceConfig, sourceModifier, lastIndexTime);
    }

    @Override
    public void init() throws IRException {

        dataMap = null;
        p = Pattern.compile("<title>(?s)(.*)(?s)</title>",Pattern.CASE_INSENSITIVE);
        lineNum = 0;
        webPageGather = new WebPageGather();

        String urlListText = getConfigString("urlList").toString();
        urlList = urlListText.split("\n");
    }

    @Override
    public boolean hasNext() throws IRException {

        String urlInfo = readURLInfo();
        dataMap = new HashMap<String, Object>();
        if(urlInfo == null)
            return false;

        String[] tmps = urlInfo.split("\t");
        for (int count = 0; count < tmps.length; count++) {
            tmps[count] = tmps[count].replaceAll("\r", "");
        }

        if (tmps.length >= 1 && tmps.length < 6) {
            String source;
            if (tmps.length <= 2) {
                source = webPageGather.getLinkPageContent(tmps[0], "utf-8", "get");
            } else if (tmps.length >= 3) {
                if (tmps[2] == null || tmps[2].equals("")) {
                    source = webPageGather.getLinkPageContent(tmps[0], "utf-8", "get");
                } else {
                    source = webPageGather.getLinkPageContent(tmps[0], tmps.length > 2 ? tmps[2] : "utf-8", "get");
                }
            } else {
                logger.error("There is error in url list parameter at line {}", lineNum);
                return false;
            }
            // ID값 입력, URL 정보 한 줄을 읽어올  때마다 1씩 증가한다. (필드 ID)
            dataMap.put("id", lineNum);
            dataMap.put("source", source);

            // 타이틀, 텍스트 파일에 미리 입력해 둔 타이틀이 있다면 해당 값을 가져오고, 그게 아니라면 타이틀 태그에서 값을 가져온다. (필드 TITLE)
            if (tmps.length == 1) {
                Matcher m = p.matcher(source);
                String title = "";
                if (m.find()) {
                    title = m.group(1);
                } else {
                    title = "";
                }
                dataMap.put("title", title);
            } else {

                if (tmps[1] == null || tmps[1].equals("")) {
                    Matcher m = p.matcher(source);
                    String title = "";
                    if (m.find()) {
                        title = m.group(1);
                    } else {
                        title = "";
                    }
                    dataMap.put("title", title);
                } else {
                    dataMap.put("title", tmps[1]);
                }
            }

            // 웹페이지 내용을 파싱 후 텍스트에 해당되는 내용만을 추출한다. 필드 CONTENT에 저장한다. 페이지 접근이 되지 않을 경우 파싱하지 않는다.
            String extracted = null;
            try {
                extracted = extractor.extract(source);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
            if(extracted == null) {
                extracted = HTMLTagRemover.clean(source);
            }
            dataMap.put("content", extracted);

            /*
            * URL 값을 색인 시 저장한다. 해당 값은 텍스트 파일에 입력한 주소를 그대로 가져온다.
            * 4번째 파라미터로 하이퍼링크 용의 주소를 별도로 입력했을 경우
            * */
            if (tmps.length == 4) {
                dataMap.put("url", tmps[0]);
                dataMap.put("link", tmps[3]);
            } else {
                dataMap.put("url", tmps[0]);
                dataMap.put("link", tmps[0]);
            }

            /*
            * 2016-03-02
            * 별도의 다섯 번째 파라미터를 받는 경우 추가
            * */
            if (tmps.length == 5) {
                dataMap.put("etc", tmps[4]);
            }
        } else {
            logger.error("There is error in url list file at line {}", lineNum);
            return false;
        }

        return true;
    }

    /*
    * 설정에 입력된 파일을 불러와 파싱을 할 URL 정보를 가져온다.
    * 입력할 정보는 URL,제목,인코딩 설정,링크주소 이다.
    * URL 이외에는 필수 입력 항목이 아니며, 제목의 경우 입력하지 않으면 title 태그에서 제목을 가져오고, 인코딩 설정은 입력하지 않을 시 기본적으로 UTF-8이다.
    * */
    private String readURLInfo() throws IRException {

        String line = "";

        if (lineNum >= urlList.length) {
            return null;
        }

        if (urlList[lineNum] == null) {
            return null;
        }

        String[] splited = urlList[lineNum].split(",");
        line = "";
        for (int cnt = 0; cnt < splited.length; cnt++) {
            line += splited[cnt] + "\t";
        }
        lineNum++;

        return line;
    }

    @Override
    protected Map<String, Object> next() throws IRException {
        return dataMap;
    }

    @Override
    protected void initParameters() {
        registerParameter(new SourceReaderParameter("urlList", "URL List", "URL List for Webpage Parsing. (URL,TITLE,Encoding,Link URL)"
                , SourceReaderParameter.TYPE_TEXT, true, null));
    }
}
