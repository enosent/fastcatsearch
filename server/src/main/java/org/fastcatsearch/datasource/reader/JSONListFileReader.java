package org.fastcatsearch.datasource.reader;

import net.minidev.json.parser.ParseException;
import org.fastcatsearch.datasource.SourceModifier;
import org.fastcatsearch.datasource.reader.annotation.SourceReader;
import org.fastcatsearch.ir.common.IRException;
import org.fastcatsearch.ir.config.SingleSourceConfig;
import org.fastcatsearch.util.JSONParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.Map;

@SourceReader(name = "JSON_LIST_FILE")
public class JSONListFileReader extends AbstractFileReader {

    private JSONParser jsonParser;

    public JSONListFileReader() {
        jsonParser = new JSONParser();
    }

    public JSONListFileReader(String collectionId, File filePath, SingleSourceConfig singleSourceConfig, SourceModifier sourceModifier, String lastIndexTime) throws IRException {
        super(collectionId, filePath, singleSourceConfig, sourceModifier, lastIndexTime);
        jsonParser = new JSONParser();
    }

    @Override
    protected Map<String, Object> parse(BufferedReader reader) throws IRException, IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("EOF");
        }
        line = line.trim();
        if (line.length() == 0) {
            return null;
        }
        try {
            return jsonParser.parse(line);
        } catch (ParseException e) {
            logger.error("error while convert json to map : " + line, e);
        }
        return null;
    }

    @Override
    protected void initReader(BufferedReader reader) throws IRException, IOException {
        //do nothing
    }


}
