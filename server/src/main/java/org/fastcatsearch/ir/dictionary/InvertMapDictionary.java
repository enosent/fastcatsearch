package org.fastcatsearch.ir.dictionary;

import org.fastcatsearch.ir.io.CharVector;
import org.fastcatsearch.plugin.analysis.AnalysisPluginSetting;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by swsong on 2015. 7. 31..
 */
public class InvertMapDictionary extends MapDictionary {

    public InvertMapDictionary() {
    }

    public InvertMapDictionary(boolean ignoreCase) {
        super(ignoreCase);
    }

    public InvertMapDictionary(File file, boolean ignoreCase) {
        super(file, ignoreCase);
    }

    @Override
    public void addEntry(String keyword, Object[] values, List<AnalysisPluginSetting.ColumnSetting> columnList) {
        if (keyword == null) {
            return;
        }
        keyword = keyword.trim();
        if(keyword.length() == 0) {
            return;
        }
        CharVector[] value = new CharVector[] { new CharVector(keyword) };

        for (int i = 0; i < values.length; i++) {
            map.put(new CharVector((String) values[i]), value);
        }
    }

    @Override
    public void clear() {
        super.clear();
        map.clear();
    }
}
