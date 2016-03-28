package org.fastcatsearch.ir.dictionary;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fastcatsearch.db.dao.DictionaryDAO;
import org.fastcatsearch.plugin.analysis.AnalysisPluginSetting.ColumnSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DAOSourceDictionaryCompiler {
	
	protected static final Logger logger = LoggerFactory.getLogger(DAOSourceDictionaryCompiler.class);
	
	private static final int BULK_SIZE = 80000;

	/*
	 * 컴파일된 엔트리 갯수를 반환한다.
	 * */
	public static int compile(File targetFile, DictionaryDAO dictionaryDAO, SourceDictionary dictionaryType, List<ColumnSetting> columnList)
			throws Exception {

		int count = dictionaryDAO.getCount(null);

		int start = 1;

		String keyColumnName = null;
		List<String> valueColumnNames = new ArrayList<String>();
		List<ColumnSetting> columnSettingList = new ArrayList<ColumnSetting>();
		// key는 설정안해도 무조건 isCompilable이다.
		for (int i = 0; i < columnList.size(); i++) {
			ColumnSetting columnSetting = columnList.get(i);
			if (columnSetting.isCompilable() && !columnSetting.isKey()) {
				valueColumnNames.add(columnSetting.getName().toUpperCase());
				columnSettingList.add(columnSetting);
			}
			if (columnSetting.isKey()) {
				keyColumnName = columnSetting.getName().toUpperCase();
			}
		}
		
		//synoym_2way처리를 위해 주석처리 swsong 2013-12-16
//		boolean isKeyNullable = dictionaryType instanceof SynonymDictionary;
//		if(!isKeyNullable && keyColumnName == null){
//			throw new Exception("Key column is not specified.");
//		}
		
		while (start <= count) {
			int end = start + BULK_SIZE;

			List<Map<String, Object>> result = dictionaryDAO.getEntryList(start, end, null, null);
			for (int i = 0; i < result.size(); i++) {
				Map<String, Object> vo = result.get(i);
				Object[] values = new Object[valueColumnNames.size()];
				String key = null;
				if(keyColumnName != null){
					key = vo.get(keyColumnName).toString().trim();
				}
				for (int j = 0; j < valueColumnNames.size(); j++) {
					String columnName = valueColumnNames.get(j);
					
					values[j] = vo.get(columnName);
				}
				dictionaryType.addEntry(key, values, columnSettingList);
			}

			if (result.size() < BULK_SIZE) {
				// 다 읽어온 것임.
				break;
			}
			start += BULK_SIZE;
		}
		OutputStream out = null;
		try {
			out = new FileOutputStream(targetFile);
			dictionaryType.writeTo(out);
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException ignore) {
				}
			}
		}

		return count;
	}
	
}
