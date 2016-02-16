package org.fastcatsearch.http.action.service;

import org.fastcatsearch.control.JobService;
import org.fastcatsearch.control.ResultFuture;
import org.fastcatsearch.http.ActionMapping;
import org.fastcatsearch.http.action.ActionRequest;
import org.fastcatsearch.http.action.ActionResponse;
import org.fastcatsearch.http.action.ServiceAction;
import org.fastcatsearch.http.writer.DemoSearchResultWriter;
import org.fastcatsearch.ir.IRService;
import org.fastcatsearch.job.search.ClusterSearchJob;
import org.fastcatsearch.query.QueryMap;
import org.fastcatsearch.service.ServiceManager;
import org.fastcatsearch.settings.SearchPageSettings;
import org.fastcatsearch.settings.SearchPageSettings.SearchCategorySetting;
import org.fastcatsearch.util.ResponseWriter;
import org.fastcatsearch.util.ResultWriterException;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@ActionMapping("/service/demo/search")
public class DemoSearchAction extends ServiceAction {

	@Override
	public void doAction(ActionRequest request, ActionResponse response) throws Exception {

		String keyword = request.getParameter("keyword");
		String category = request.getParameter("category");
		int page = request.getIntParameter("page", 1);

		writeHeader(response);
		
		ResponseWriter responseWriter = getResponseWriter(response.getWriter(), ServiceAction.DEFAULT_ROOT_ELEMENT, true, null, false, false);
		long st = System.nanoTime();
		
		responseWriter.object();
		responseWriter.key("category").value(category != null ? category : "");
		responseWriter.key("keyword").value(keyword != null ? keyword : "");
		
		try {
			IRService irService = ServiceManager.getInstance().getService(IRService.class);
			SearchPageSettings searchPageSettings = irService.getSearchPageSettings();

			String realtimePopularKeywordURL = searchPageSettings.getRealtimePopularKeywordURL();
			String relateKeywordURL = searchPageSettings.getRelateKeywordURL();
			
			if(relateKeywordURL != null){
				relateKeywordURL = replaceKeyword(relateKeywordURL, keyword);
			}
			
			responseWriter.key("realtimePopularKeywordURL").value(realtimePopularKeywordURL != null ? realtimePopularKeywordURL : "");
			responseWriter.key("relateKeywordURL").value(relateKeywordURL != null ? relateKeywordURL : "");
			responseWriter.key("javascript").value(searchPageSettings.getJavascript());
			responseWriter.key("css").value(searchPageSettings.getCss());
//			logger.debug(">>> css > {}", searchPageSettings.getCss());
			List<SearchCategorySetting> searchCategorySettingList = searchPageSettings.getSearchCategorySettingList();
			
			TreeMap<Integer, SearchCategorySetting> map = new TreeMap<Integer, SearchCategorySetting>();
			//order 로 우선순위를 구한다.
			for (SearchCategorySetting setting : searchCategorySettingList) {
				int order = Integer.parseInt(setting.getOrder());
				map.put(order, setting);
			}
			
			responseWriter.key("category-list").array();
			for (SearchCategorySetting s : map.values()) {
				responseWriter.object().key("id").value(s.getId())
				.key("name").value(s.getName()).endObject();
			}
			responseWriter.endArray();
			
			responseWriter.key("result-list").array();
//			logger.debug("category > [{}], map[{}]", category, map);
			if (category != null && category.length() > 0) {
				//개별 검색시 보여줄 리스트갯수.
				int searchListSize = searchPageSettings.getSearchListSize();
				// 하나만 고른다.
				SearchCategorySetting setting = null;
				for (SearchCategorySetting s : searchCategorySettingList) {
					if (s.getId().equals(category)) {
						setting = s;
						break;
					}
				}
//				logger.debug("SearchCategorySetting > {}", setting);
				if(setting != null) {
					writeSettingSearchResult(setting, keyword, page, searchListSize, responseWriter);
				}
			} else {
				//통합검색시 보여줄 리스트갯수.
				int totalSearchListSize = searchPageSettings.getTotalSearchListSize();
				for (SearchCategorySetting setting : map.values()) {
//					logger.debug("SearchCategorySetting2 > {}, {}", setting.getId(), setting);
					writeSettingSearchResult(setting, keyword, page, totalSearchListSize, responseWriter);
				}
			}
			
			responseWriter.endArray();

		}catch(Exception e){
			logger.error("", e);
		} finally {
			int time = (int) ((System.nanoTime() - st) / 1000000);
			String timeString = String.format("%.2f", ((float)time) / 1000.0f);
			responseWriter.key("time").value(timeString);
			
			responseWriter.endObject();
			responseWriter.done();
		}

	}
	
	private void writeSettingSearchResult(SearchCategorySetting setting, String keyword, int page, int searchListSize, ResponseWriter responseWriter) throws ResultWriterException, IOException{
		String queryString = setting.getSearchQuery();
		queryString = replaceKeyword(queryString, keyword);
		QueryMap queryMap = parse(queryString);
		int sn = (page - 1) * searchListSize + 1;
		queryMap.put("sn", Integer.toString(sn));
		queryMap.put("ln", Integer.toString(searchListSize));
		
		logger.debug("queryMap > {}", queryMap.queryString());
		ClusterSearchJob searchJob = new ClusterSearchJob();
		searchJob.setArgs(queryMap);
		long st = System.nanoTime();
		ResultFuture resultFuture = JobService.getInstance().offer(searchJob);
		Object result = resultFuture.take();
		long searchTime = (System.nanoTime() - st) / 1000000;
		responseWriter.object();
		responseWriter.key("id").value(setting.getId());
		responseWriter.key("name").value(setting.getName().toUpperCase());
		responseWriter.key("searchListSize").value(searchListSize);
		
		responseWriter.key("result");
		DemoSearchResultWriter searchResultWriter = new DemoSearchResultWriter(responseWriter, setting);
		searchResultWriter.writeResult(result, searchTime, resultFuture.isSuccess());
		responseWriter.endObject();
	}
	
	private String replaceKeyword(String tagetString, String keyword) {
		if(tagetString == null){
			return null;
		}
		
		return tagetString.replaceAll("#keyword", keyword);
	}
	
	private QueryMap parse(String queryString) {
		
		Map<String, String> parameterMap = new HashMap<String, String>();
		String collectionId = null;
		for (String pair : queryString.split("&")) {
			int eq = pair.indexOf("=");
			if (eq < 0) {
				// key with no value
				// parameterMap.put(pair.toUpperCase(), "");
				parameterMap.put(pair, "");
			} else {
				// key=value
				String key = pair.substring(0, eq);
				String value = pair.substring(eq + 1);
				try {
					String decodedValue = URLDecoder.decode(value, "utf-8");
					value = decodedValue;
//					logger.debug("DECODE {} > {}", value, decodedValue);
				} catch (Exception e) {
					// 디코드 에러시 디코드하지 않음. '100%보증'과 같은 문자가 들어올수 있음.
				}
				parameterMap.put(key, value);
				if(key.equalsIgnoreCase("cn")){
					collectionId = value;
				}

			}
		}
		QueryMap queryMap =  new QueryMap(parameterMap);
		queryMap.setId(collectionId);
		// logger.debug("parameterMap >> {}", parameterMap);
		return queryMap;
	}

}
