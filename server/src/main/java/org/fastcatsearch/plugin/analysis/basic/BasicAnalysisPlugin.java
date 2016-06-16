package org.fastcatsearch.plugin.analysis.basic;

import org.apache.lucene.analysis.core.CSVAnalyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.fastcatsearch.ir.analysis.AutocompleteAnalyzer;
import org.fastcatsearch.ir.analysis.DefaultAnalyzerFactory;
import org.fastcatsearch.ir.analysis.NGramWordAnalyzer;
import org.fastcatsearch.ir.analysis.NGram13WordAnalyzer;
import org.fastcatsearch.ir.analysis.PrimaryWordAnalyzer;
import org.fastcatsearch.ir.dic.Dictionary;
import org.fastcatsearch.ir.dic.PreResult;
import org.fastcatsearch.ir.io.CharVector;
import org.fastcatsearch.plugin.LicenseInvalidException;
import org.fastcatsearch.plugin.PluginLicenseInfo;
import org.fastcatsearch.plugin.PluginSetting;
import org.fastcatsearch.plugin.analysis.AnalysisPlugin;
import org.fastcatsearch.plugin.analysis.AnalysisPluginSetting.DictionarySetting;
import org.fastcatsearch.plugin.analysis.AnalyzerInfo;

import java.io.File;
import java.util.Map;


public class BasicAnalysisPlugin extends AnalysisPlugin<CharVector, PreResult<CharVector>> {

	public BasicAnalysisPlugin(File pluginDir, PluginSetting pluginSetting, String serverId) throws LicenseInvalidException {
		super(pluginDir, pluginSetting, serverId);
        setLicenseInfo(new PluginLicenseInfo("analyzer-basic", "Unlimited", "Everyone"));
	}

	@Override
	protected void loadAnalyzerFactory(Map<String, AnalyzerInfo> analyzerFactoryMap) {
		//extract entire word 
		registerAnalyzer(analyzerFactoryMap, "keyword", "Keyword Analyzer", new DefaultAnalyzerFactory(KeywordAnalyzer.class));
		//lucene StandardAnalyzer
		registerAnalyzer(analyzerFactoryMap, "standard", "Standard Analyzer", new DefaultAnalyzerFactory(StandardAnalyzer.class));
		
		registerAnalyzer(analyzerFactoryMap, "ngram", "NGram Analyzer", new DefaultAnalyzerFactory(NGramWordAnalyzer.class));

		registerAnalyzer(analyzerFactoryMap, "ngram13", "NGram13 Analyzer", new DefaultAnalyzerFactory(NGram13WordAnalyzer.class));
		
		registerAnalyzer(analyzerFactoryMap, "primary", "Primary Word Analyzer", new DefaultAnalyzerFactory(PrimaryWordAnalyzer.class));
		
		registerAnalyzer(analyzerFactoryMap, "whitespace", "Whitespace Analyzer", new DefaultAnalyzerFactory(WhitespaceAnalyzer.class));
		
		registerAnalyzer(analyzerFactoryMap, "csv", "Comma separated value Analyzer", new DefaultAnalyzerFactory(CSVAnalyzer.class));

        registerAnalyzer(analyzerFactoryMap, "autocomplete", "Autocomplete Analyzer", new DefaultAnalyzerFactory(AutocompleteAnalyzer.class));
	}

	@Override
	protected Dictionary<CharVector, PreResult<CharVector>> loadSystemDictionary(DictionarySetting dictionarySetting) {
		return null;
	}

}
