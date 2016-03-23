package org.fastcatsearch.http.action.management.dictionary;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fastcatsearch.db.dao.DictionaryDAO;
import org.fastcatsearch.http.ActionAuthority;
import org.fastcatsearch.http.ActionAuthorityLevel;
import org.fastcatsearch.http.ActionMapping;
import org.fastcatsearch.http.action.ActionRequest;
import org.fastcatsearch.http.action.ActionResponse;
import org.fastcatsearch.http.action.AuthAction;
import org.fastcatsearch.plugin.Plugin;
import org.fastcatsearch.plugin.PluginService;
import org.fastcatsearch.plugin.analysis.AnalysisPlugin;
import org.fastcatsearch.plugin.analysis.AnalysisPluginSetting;
import org.fastcatsearch.plugin.analysis.AnalysisPluginSetting.ColumnSetting;
import org.fastcatsearch.plugin.analysis.AnalysisPluginSetting.DictionarySetting;
import org.fastcatsearch.service.ServiceManager;
import org.fastcatsearch.util.ResponseWriter;

@ActionMapping(value="/management/dictionary/list", authority=ActionAuthority.Dictionary, authorityLevel=ActionAuthorityLevel.READABLE)
public class GetDictionaryWordListAction extends AuthAction {

	@Override
	public void doAuthAction(ActionRequest request, ActionResponse response) throws Exception {
		String pluginId = request.getParameter("pluginId");
		String dictionaryId = request.getParameter("dictionaryId");
		String search = request.getParameter("search");
		int start = request.getIntParameter("start");
		int length = request.getIntParameter("length");
		String searchColumns = request.getParameter("searchColumns");
		boolean sortAsc = request.getBooleanParameter("sortAsc", false);
		
		PluginService pluginService = ServiceManager.getInstance().getService(PluginService.class);
		Plugin plugin = pluginService.getPlugin(pluginId);
		AnalysisPlugin analysisPlugin = (AnalysisPlugin) plugin;
		
		AnalysisPluginSetting analysisPluginSetting = (AnalysisPluginSetting) plugin.getPluginSetting();
		List<DictionarySetting> dictionaryList = analysisPluginSetting.getDictionarySettingList();
		
		List<String> columnNameList = new ArrayList<String>();
		List<String> searchableColumnList = new ArrayList<String>(); 
		if(dictionaryList != null){
			for(DictionarySetting dictionary : dictionaryList){
				if(dictionary.getId().equals(dictionaryId)){
					List<ColumnSetting> columnSettingList = dictionary.getColumnSettingList();
					if(columnSettingList!=null) {
						for(int i=0;i<columnSettingList.size(); i++){
							ColumnSetting columnSetting = columnSettingList.get(i);
							if(columnSetting.isSearchable()){
								searchableColumnList.add(columnSetting.getName());
							}
							columnNameList.add(columnSetting.getName());
						}
					}
				}
			}
		}
		
		
		Writer writer = response.getWriter();
		ResponseWriter resultWriter = getDefaultResponseWriter(writer);
		
		int totalSize = 0;
		int filteredSize = 0;
		DictionaryDAO dictionaryDAO = analysisPlugin.getDictionaryDAO(dictionaryId);
		resultWriter.object().key(dictionaryId).array();
		
		String[] searchColumnList = null;
		if(searchColumns != null && searchColumns.trim().length() > 0){
			searchColumnList = searchColumns.split(",");
		}
		
		//logger.debug("searchColumns > {}, {}", searchColumns, searchColumnList);
		if(dictionaryDAO != null){
			totalSize = dictionaryDAO.getCount(null, null);
			filteredSize = dictionaryDAO.getCount(search, searchColumnList);
			
			if(length==-1) {
				length = totalSize;
			}
			
			List<Map<String, Object>> list = dictionaryDAO.getEntryList(start, start + length - 1, search, searchColumnList, sortAsc);
			final String ID_COLUMN = "ID";
			List<ColumnSetting> columnSettingList = dictionaryDAO.columnSettingList();
			//if(columnSettingList != null){
				for(Map<String, Object> vo : list){
					resultWriter.object().key(ID_COLUMN).value(vo.get(ID_COLUMN));
					for(int i = 0 ;i < columnSettingList.size(); i++){
						ColumnSetting columnSetting = columnSettingList.get(i);
						String name = columnSetting.getName().toUpperCase();
						resultWriter.key(name).value(vo.get(name));
					}
						
					resultWriter.endObject();
				}
			//}
		}
		
		resultWriter.endArray();
		
		resultWriter.key("totalSize").value(totalSize).key("filteredSize").value(filteredSize);
		
		resultWriter.key("searchableColumnList").array();
		for(String columnName : searchableColumnList){
			resultWriter.value(columnName.toUpperCase());
		}
		resultWriter.endArray();
		
		resultWriter.key("columnList").array();
		for(String columnName : columnNameList){
			resultWriter.value(columnName.toUpperCase());
		}
		resultWriter.endArray();
		
		resultWriter.endObject();
		resultWriter.done();
			
	}

}
