/*
 * Copyright (c) 2013 Websquared, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *	 swsong - initial API and implementation
 */

package org.fastcatsearch.datasource.reader;

import org.fastcatsearch.datasource.SourceModifier;
import org.fastcatsearch.ir.common.IRException;
import org.fastcatsearch.ir.config.DataSourceConfig;
import org.fastcatsearch.ir.config.SingleSourceConfig;
import org.fastcatsearch.ir.settings.SchemaSetting;
import org.fastcatsearch.util.DynamicClassLoader;
import org.jdom.JDOMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class DefaultDataSourceReaderFactory {
	private static Logger logger = LoggerFactory.getLogger(DefaultDataSourceReaderFactory.class);

	public static AbstractDataSourceReader<Map<String, Object>> createFullIndexingSourceReader(String collectionId, File filePath, SchemaSetting schemaSetting, DataSourceConfig dataSourceConfig) throws IRException, JDOMException, IOException {

		AbstractDataSourceReader<Map<String, Object>> dataSourceReader = new DefaultDataSourceReader(schemaSetting);
		logger.debug("dataSourceConfig > {}", dataSourceConfig);
		if (dataSourceConfig != null && dataSourceConfig.getFullIndexingSourceConfig() != null) {
			
			for (SingleSourceConfig singleSourceConfig : dataSourceConfig.getFullIndexingSourceConfig()) {
				if(!singleSourceConfig.isActive()){
					continue;
				}
				SingleSourceReader<Map<String, Object>> sourceReader = createSingleSourceReader(collectionId, filePath, singleSourceConfig, null);
				dataSourceReader.addSourceReader(sourceReader);
			}
		} else {
			logger.error("No data source config!");
		}
		dataSourceReader.init();
		return dataSourceReader;
	}
	
	public static AbstractDataSourceReader<Map<String, Object>> createAddIndexingSourceReader(String collectionId, File filePath, SchemaSetting schemaSetting, DataSourceConfig dataSourceConfig, String lastIndexTime) throws IRException, JDOMException, IOException {

		AbstractDataSourceReader<Map<String, Object>> dataSourceReader = new DefaultDataSourceReader(schemaSetting);
		logger.debug("dataSourceConfig > {}", dataSourceConfig);
		if (dataSourceConfig != null && dataSourceConfig.getAddIndexingSourceConfig() != null) {
			
			for (SingleSourceConfig singleSourceConfig : dataSourceConfig.getAddIndexingSourceConfig()) {
				if(!singleSourceConfig.isActive()){
					continue;
				}
				SingleSourceReader<Map<String, Object>> sourceReader = createSingleSourceReader(collectionId, filePath, singleSourceConfig, lastIndexTime);
				dataSourceReader.addSourceReader(sourceReader);
			}
		} else {
			logger.error("No data source config!");
		}
		dataSourceReader.init();
		return dataSourceReader;
	}

	private static SingleSourceReader<Map<String, Object>> createSingleSourceReader(String collectionId, File filePath, SingleSourceConfig singleSourceConfig, String lastIndexTime) throws IRException {
		String sourceReaderType = singleSourceConfig.getSourceReader();
		String sourceModifierType = singleSourceConfig.getSourceModifier();
		SourceModifier<Map<String, Object>> sourceModifier = null;
		if (sourceModifierType != null && sourceModifierType.length() > 0) {
			sourceModifier = DynamicClassLoader.loadObject(sourceModifierType, SourceModifier.class);
			if(sourceModifier == null) {
				logger.error("Cannot find source modifier : {}", sourceModifierType);
			}
		}

		SingleSourceReader<Map<String, Object>> sourceReader = null;
		try {
			sourceReader = DynamicClassLoader.loadObject(sourceReaderType, SingleSourceReader.class, 
		   		new Class[]{ String.class, File.class, SingleSourceConfig.class, SourceModifier.class, String.class}, 
				new Object[]{collectionId, filePath, singleSourceConfig, sourceModifier, lastIndexTime});
		} catch (Exception e) {
			throw new IRException(e);
		}

		logger.debug("Loading sourceReader : {} >> {}, modifier:{} / lastIndexTime:{}", sourceReaderType, sourceReader, sourceModifier, lastIndexTime);
		// dataSourceReader가 null일 수 있다.
		if (sourceReader == null) {
			throw new IRException("Cannot find source reader. Make sure the class is in classpath or constructor signature is valid. reader = " + sourceReaderType);
		} else {
			return sourceReader;
		}

	}
}
