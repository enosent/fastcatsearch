package org.fastcatsearch.util;

import net.minidev.json.JSONAwareEx;
import net.minidev.json.parser.ParseException;
import net.minidev.json.writer.JsonReader;
import net.minidev.json.writer.JsonReaderI;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.minidev.json.parser.JSONParser.MODE_JSON_SIMPLE;

/**
 * Created by swsong on 2016. 2. 11..
 */

public class JSONParser {
    private net.minidev.json.parser.JSONParser parser;
    private JsonMapperLowerKey mapper;

    public JSONParser() {
        this(MODE_JSON_SIMPLE);
    }

    public JSONParser(int mode) {
        mapper = new JsonMapperLowerKey(new JsonReader());
        parser = new net.minidev.json.parser.JSONParser(mode);
    }
    public Map<String, Object> parse(String jsonString) throws ParseException {
        return parser.parse(jsonString, mapper);
    }

    public Map<String, Object> parse(Reader reader) throws ParseException {
        return parser.parse(reader, mapper);
    }
    class JsonMapperLowerKey extends JsonReaderI<Map<String, Object>> {

        /**
         * Reader can be link to the JsonReader Base
         *
         * @param base
         */
        public JsonMapperLowerKey(JsonReader base) {
            super(base);
        }

        @Override
        public JsonReaderI<JSONAwareEx> startObject(String key) {
            return base.DEFAULT;
        }

        @Override
        public JsonReaderI<JSONAwareEx> startArray(String key) {
            return base.DEFAULT;
        }

        @Override
        public Object createObject() {
            return new HashMap<String, Object>();
        }

        @Override
        public Object createArray() {
            return new ArrayList<Object>();
        }

        @Override
        public void setValue(Object current, String key, Object value) {
            ((Map<String, Object>) current).put(key.toLowerCase(), value);
        }

        @Override
        public void addValue(Object current, Object value) {
            ((List<Object>) current).add(value);
        }
    }
}
