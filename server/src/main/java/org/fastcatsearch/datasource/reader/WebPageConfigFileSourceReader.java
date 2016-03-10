package org.fastcatsearch.datasource.reader;

import com.esotericsoftware.yamlbeans.YamlReader;
import org.fastcatsearch.datasource.SourceModifier;
import org.fastcatsearch.datasource.reader.annotation.SourceReader;
import org.fastcatsearch.ir.common.IRException;
import org.fastcatsearch.ir.config.SingleSourceConfig;
import org.fastcatsearch.util.ReadabilityExtractor;
import org.fastcatsearch.util.WebPageGather;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by 전제현 on 2016-02-22.
 * 설정 파일에서 URL 정보를 읽어와 해당 URL의 내용을 파싱하여 색인한다.
 */
@SourceReader(name="WEBPAGE_CONFIG")
public class WebPageConfigFileSourceReader extends SingleSourceReader<Map<String, Object>> {

    protected static Logger logger = LoggerFactory.getLogger(WebPageConfigFileSourceReader.class);

    private static String TYPE_XML = "xml";
    private static String TYPE_YML = "yml";
    private static String TYPE_JSON = "json";

    private Map<String, Object> dataMap;
    private List sourceList;
    private int idx;
    private Pattern p;
    private SimpleDateFormat wdate;
    private WebPageGather webPageGather;

    public WebPageConfigFileSourceReader() {
        super();
    }

    public WebPageConfigFileSourceReader(String collectionId, File filePath, SingleSourceConfig singleSourceConfig, SourceModifier<Map<String, Object>> sourceModifier, String lastIndexTime) throws IRException {
        super(collectionId, filePath, singleSourceConfig, sourceModifier, lastIndexTime);
    }

    @Override
    public void init() throws IRException {

        String configType = getConfigString("configFileType").toString();
        File configFile = new File(getConfigString("configFilePath").toString());
        SAXBuilder builder = new SAXBuilder();

        dataMap = null;
        p = Pattern.compile("<title>(?s)(.*)(?s)</title>", Pattern.CASE_INSENSITIVE);
        wdate = new SimpleDateFormat("yyyyMMddHHmmss");
        sourceList = new ArrayList();
        webPageGather = new WebPageGather();

        if (!configFile.exists()) {
            logger.error("There is no Source File.");
        }

        if (configType.equalsIgnoreCase(TYPE_XML)) {

            Document doc = null;
            Element root = null;
            try {
                doc = builder.build(configFile);
                root = doc.getRootElement();

                List list = root.getChild("document").getChildren("entity");
                String[] configParameter = root.getChild("document").getChild("attributes").getAttributeValue("value").split("\\,");

                for (int cnt = 0; cnt < list.size(); cnt++) {
                    Element el = (Element) list.get(cnt);

                    List attributes = ((Element) list.get(cnt)).getAttributes();
                    Map sdata = new HashMap();
                    for (int attributeCnt = 0; attributeCnt < configParameter.length; attributeCnt++) {
                        String attribute = ((Attribute) attributes.get(attributeCnt)).getName();
                        sdata.put(attribute, el.getAttributeValue(attribute));
                    }
                    sourceList.add(sdata);
                }
            } catch (IOException e) {
                logger.error(e.toString());
            } catch (JDOMException e) {
                logger.error(e.toString());
            }

        } else if (configType.equalsIgnoreCase(TYPE_YML)) {

            YamlReader reader = null;

            try {
                reader = new YamlReader(new FileReader(configFile));
                while (true) {
                    Map sdata = (Map) reader.read();
                    if (sdata == null) break;
                    sourceList.add(sdata);
                }
                reader.close();
            } catch (IOException e) {
                logger.error(e.toString());
            } finally {
                try { reader.close(); } catch (IOException e) { logger.error(e.toString()); }
            }

        } else if (configType.equalsIgnoreCase(TYPE_JSON)) {

            FileReader jsonFile = null;

            try {
                jsonFile = new FileReader(configFile);
                JSONParser parser = new JSONParser();
                Object obj = parser.parse(jsonFile);
                JSONObject jsonObject = (JSONObject) obj;

                JSONArray list = (JSONArray) jsonObject.get("list");
                for (int cnt = 0; cnt < list.size(); cnt++) {
                    JSONObject listObj = (JSONObject) list.get(cnt);
                    if (listObj != null) {
                        Map sdata = (Map) listObj;
                        sourceList.add(sdata);
                    }
                }

            } catch (FileNotFoundException e) {
                logger.error(e.toString());
            } catch (IOException e) {
                logger.error(e.toString());
            } catch (ParseException e) {
                logger.error(e.toString());
            } finally {
                try { jsonFile.close(); } catch (IOException e) { logger.error(e.toString()); }
            }

        } else {

            throw new IRException("Input File Type one out of the Type in XML, YML, JSON.");
        }
    }

    @Override
    public boolean hasNext() throws IRException {

        Map<String,Object> oneDoc = readOneDoc();
        if (oneDoc == null) {
            return false;
        }

        String url = (String)oneDoc.get("url");
        String charset = (String)oneDoc.get("charset");

        if (charset == null || charset.equals("")) {
            charset = "utf-8";
        }

        if (url == null || url.equals("")) {
            logger.debug((new StringBuilder()).append("There is no url in entity").append(idx).toString());
        } else {
            String source = webPageGather.getLinkPageContent(url, charset, "get");
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

            oneDoc.put("id", idx);
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
        registerParameter(new SourceReaderParameter("configFileType", "Full indexing Config File Type", "Config File Type for Full indexing Webpage Parsing. (XML, YML, JSON)"
                , SourceReaderParameter.TYPE_STRING, true, null));
        registerParameter(new SourceReaderParameter("configFilePath", "Full indexing Config File Path", "Config File for Full indexing Webpage Parsing."
                , SourceReaderParameter.TYPE_STRING_LONG, true, null));
    }
}