package org.fastcatsearch.datasource.reader;

import org.apache.derby.iapi.sql.ResultSet;
import org.fastcatsearch.datasource.SourceModifier;
import org.fastcatsearch.datasource.reader.annotation.SourceReader;
import org.fastcatsearch.env.Environment;
import org.fastcatsearch.ir.common.IRException;
import org.fastcatsearch.ir.config.SingleSourceConfig;
import org.fastcatsearch.ir.document.Document;
import org.fastcatsearch.ir.index.PrimaryKeys;
import org.fastcatsearch.ir.io.BufferedFileOutput;
import org.fastcatsearch.ir.io.DirBufferedReader;
import org.fastcatsearch.util.HTMLTagRemover;
import org.fastcatsearch.util.ReadabilityExtractor;
import org.fastcatsearch.util.WebPageGather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by 전제현 on 2016-02-22.
 */
@SourceReader(name="WEBPAGE_PARSING")
public class WebPageSourceReader extends SingleSourceReader<Map<String, Object>> {

    protected static Logger logger = LoggerFactory.getLogger(WebPageSourceReader.class);

    private DirBufferedReader br;
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
        p = Pattern.compile("<title>(.*)</title>",Pattern.CASE_INSENSITIVE);
        lineNum = 0;
        webPageGather = new WebPageGather();

        String fileEncoding = getConfigString("encoding");
        if (fileEncoding == null) {
            fileEncoding = Charset.defaultCharset().toString();
        }
        try {
            File file = filePath.makePath(getConfigString("filepath")).file();
            br = new DirBufferedReader(file, fileEncoding);
            logger.info("Collect file = {}, {}", file.getAbsolutePath(), fileEncoding);
        } catch (UnsupportedEncodingException e) {
            logger.error(e.getMessage(), e);
            throw new IRException(e);
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage(), e);
            throw new IRException(e);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new IRException(e);
        }
    }

    @Override
    public boolean hasNext() throws IRException {

        String urlInfo = readURLInfo();
        dataMap = new HashMap<String, Object>();
        if(urlInfo == null)
            return false;

        String[] tmps = urlInfo.split("\t");

        if (tmps.length >= 1) {
            String source = webPageGather.getLinkPageContent(tmps[0], tmps.length>2?tmps[2]:"utf-8", "get");
            //id
            dataMap.put("id", lineNum);

            //title
            if (tmps.length == 1) {
                Matcher m = p.matcher(source);
                String title = "";
                if (m.find()) {
                    title = m.group(1);
                }else{
                    if (source.length() > 10) {
                        title = source.substring(0,10);
                    }else{
                        title = source;
                    }
                }
                dataMap.put("title", title);
            } else {

                if (tmps[1] == null) {
                    Matcher m = p.matcher(source);
                    String title = "";
                    if (m.find()) {
                        title = m.group(1);
                    }else{
                        if (source.length() > 10) {
                            title = source.substring(0,10);
                        }else{
                            title = source;
                        }
                    }
                    dataMap.put("title", title);
                } else {
                    dataMap.put("title", tmps[1]);
                }
            }

            //content
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

            //url
            dataMap.put("url", tmps[0]);
        } else {
            logger.error("There is error in url list file at line "+lineNum);
            return false;
        }

        return true;
    }

    /*
    * 설정에 입력된 파일을 불러와 파싱을 할 URL 정보를 가져온다.
    * 입력할 정보는 URL,제목,인코딩 설정 이다.
    * URL 이외에는 필수 입력 항목이 아니며, 제목의 경우 입력하지 않으면 title 태그에서 제목을 가져오고, 인코딩 설정은 입력하지 않을 시 기본적으로 UTF-8이다.
    * */
    private String readURLInfo() throws IRException {

        String line = "";

        try{

            line = br.readLine();

            if(line == null)
                return null;

            String[] splited = line.split(",");
            line = "";
            for (int count = 0; count < splited.length; count++) {
                line += splited[count] + "\t";
            }
            lineNum++;



        } catch (UnsupportedEncodingException e) {
            logger.error(e.getMessage(), e);
            throw new IRException(e);
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage(), e);
            throw new IRException(e);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new IRException(e);
        }

        return line;
    }

    @Override
    protected Map<String, Object> next() throws IRException {
        return dataMap;
    }

    @Override
    public void close() throws IRException {
        try {
            if (br != null) {
                br.close();
            }
        } catch (IOException e) {
            throw new IRException(e);
        }
    }

    @Override
    protected void initParameters() {
        registerParameter(new SourceReaderParameter("filepath", "URL List TextFile Path", "TextFile Path for Webpaage Parsing."
                , SourceReaderParameter.TYPE_STRING_LONG, true, null));
        registerParameter(new SourceReaderParameter("encoding", "Encoding", "TextFile encoding"
                , SourceReaderParameter.TYPE_STRING, true, null));
    }
}
