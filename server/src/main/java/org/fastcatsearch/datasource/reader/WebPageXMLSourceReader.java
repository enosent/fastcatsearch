package org.fastcatsearch.datasource.reader;

import org.fastcatsearch.datasource.SourceModifier;
import org.fastcatsearch.datasource.reader.annotation.SourceReader;
import org.fastcatsearch.ir.common.IRException;
import org.fastcatsearch.ir.config.SingleSourceConfig;
import org.fastcatsearch.util.ReadabilityExtractor;
import org.fastcatsearch.util.WebPageGather;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by 전제현 on 2016-02-22.
 * XML 파일에서 URL 정보를 읽어와 해당 URL의 내용을 파싱하여 색인한다. (웹페이지 색인 - XML 설정)
 */
@SourceReader(name="WEBPAGE_XMLCONFIG")
public class WebPageXMLSourceReader extends SingleSourceReader<Map<String, Object>> {

    protected static Logger logger = LoggerFactory.getLogger(WebPageXMLSourceReader.class);

    private Map<String, Object> dataMap;
    private Element root;
    private List sourceList;
    private int idx;
    private Pattern p;
    private SimpleDateFormat wdate;
    private WebPageGather webPageGather;

    public WebPageXMLSourceReader() {
        super();
    }

    public WebPageXMLSourceReader(String collectionId, File filePath, SingleSourceConfig singleSourceConfig, SourceModifier<Map<String, Object>> sourceModifier, String lastIndexTime) throws IRException {
        super(collectionId, filePath, singleSourceConfig, sourceModifier, lastIndexTime);
    }

    @Override
    public void init() throws IRException {

        dataMap = null;
        p = Pattern.compile("<title>(?s)(.*)(?s)</title>", Pattern.CASE_INSENSITIVE);
        wdate = new SimpleDateFormat("yyyyMMddHHmmss");
        File f = null;
        sourceList = new ArrayList();
        f = new File(getConfigString("xmlFilePath").toString());
        Document doc = null;
        webPageGather = new WebPageGather();

        if (!f.exists()) {
            logger.error("There is no Source File.");
        }
        SAXBuilder builder = new SAXBuilder();

        try {
            doc = builder.build(f);
        } catch (IOException e) {
            logger.error(e.toString());
        } catch (JDOMException e) {
            logger.error(e.toString());
        }

        root = doc.getRootElement();

        List list = root.getChild("document").getChildren("entity");
        for(int i = 0; i < list.size(); i++) {
            Element el = (Element) list.get(i);
            Map sdata = new HashMap();
            sdata.put("id", (new StringBuilder()).append("").append(i).toString());
            sdata.put("link", el.getAttributeValue("url"));
            sdata.put("cat1", el.getAttributeValue("cat1"));
            sdata.put("cat2", el.getAttributeValue("cat2"));
            sdata.put("cat3", el.getAttributeValue("cat3"));
            sdata.put("etc1", el.getAttributeValue("etc1"));
            sdata.put("etc2", el.getAttributeValue("etc2"));
            sdata.put("charset", el.getAttributeValue("charset"));
            sourceList.add(sdata);
        }
    }

    @Override
    public boolean hasNext() throws IRException {

        Map<String,Object> oneDoc = readOneDoc();
        if (oneDoc == null) {
            return false;
        }

        String link = (String)oneDoc.get("link");
        String charset = (String)oneDoc.get("charset");

        if (charset == null || charset.equals("")) {
            charset = "utf-8";
        }

        if (link == null || link.equals("")) {
            logger.debug((new StringBuilder()).append("There is no url in entity").append(idx).toString());
        } else {
            String source = webPageGather.getLinkPageContent(link, charset, "get");
            String content = (new ReadabilityExtractor()).extract(source);
            if(content == null)
                content = "";
            oneDoc.put("content", content);
            Matcher m = p.matcher(source);
            String title = "";
            if (m.find()) {
                title = m.group(1);
            } else {
                title = "";
            }
            oneDoc.put("title", title);
            oneDoc.put("wdate", wdate.format(new Date()));
        }

        dataMap = oneDoc;

        return true;
    }

    private Map<String,Object> readOneDoc() throws IRException {
        if(idx < sourceList.size())
            return (Map) sourceList.get(idx++);
        else
            return null;
    }

    @Override
    protected Map<String, Object> next() throws IRException {
        return dataMap;
    }

    @Override
    protected void initParameters() {
        registerParameter(new SourceReaderParameter("xmlFilePath", "Full indexing XML File Path", "XML File for Full indexing Webpage Parsing."
                , SourceReaderParameter.TYPE_STRING_LONG, true, null));
    }
}