package org.fastcatsearch.ir.search;

import org.fastcatsearch.ir.common.IRException;
import org.fastcatsearch.ir.document.Document;
import org.fastcatsearch.ir.document.DocumentReader;
import org.fastcatsearch.ir.group.GroupDataGenerator;
import org.fastcatsearch.ir.group.GroupHit;
import org.fastcatsearch.ir.group.GroupsData;
import org.fastcatsearch.ir.io.BitSet;
import org.fastcatsearch.ir.io.FixedHitStack;
import org.fastcatsearch.ir.io.FixedMaxPriorityQueue;
import org.fastcatsearch.ir.query.*;
import org.fastcatsearch.ir.search.clause.*;
import org.fastcatsearch.ir.settings.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;

/**
 * Single Thread로 동작한다.
 * 동시에 여러 thread에서 함께 사용할 수 없으며, 한번에 하나의 검색에만 사용.
 * */
public class SegmentSearcher {
	private static Logger logger = LoggerFactory.getLogger(SegmentSearcher.class);

	private final static int BULK_SIZE = 100;
	private FixedMaxPriorityQueue<HitElement> ranker;
	private GroupDataGenerator groupGenerator;
	private HitFilter hitFilter;
	private HitFilter groupHitFilter;
	private DocumentReader documentReader;
	private SegmentReader segmentReader;
	private Schema schema;

	private int totalCount;

	private HighlightInfo highlightInfo;
	private Explanation explanation;
	
	public SegmentSearcher(SegmentReader segmentReader) {
		this.segmentReader = segmentReader;
		this.schema = segmentReader.schema();
		highlightInfo = new HighlightInfo();
	}

	public Document getDocument(int docNo) throws IOException {
		if (documentReader == null) {
			documentReader = segmentReader.newDocumentReader();
		}
		return documentReader.readDocument(docNo);
	}
	
	//true인 index의 필드값만 채워진다.
	public Document getDocument(int docNo, boolean[] fieldSelectOption) throws IOException {
		if (documentReader == null) {
			documentReader = segmentReader.newDocumentReader();
		}
		Document document = documentReader.readDocument(docNo, fieldSelectOption);
		return document;
	}

//	public Hit searchHit(Query query) throws ClauseException, IOException, IRException {
//		return searchHit(query, null);
//	}
//	public Hit searchHit(Query query, PkScoreList boostList) throws ClauseException, IOException, IRException {
////		QueryModifier queryModifier = query.getMeta().queryModifier();
////		if(queryModifier != null){
////			query = queryModifier.modify(query);
////		}
//		search(query.getMeta(), query.getClause(), query.getFilters(), query.getGroups(), query.getGroupFilters(), query.getSorts(), query.getBundle(), boostList);
//		return new Hit(rankHitList(), makeGroupData(), totalCount, highlightInfo, explanation);
//	}
	public HitReader searchHitReader(Query query, PkScoreList boostList) throws ClauseException, IOException, IRException {
//		QueryModifier queryModifier = query.getMeta().queryModifier();
//		if(queryModifier != null){
//			query = queryModifier.modify(query);
//		}
		return searchHitReader(query.getMeta(), query.getClause(), query.getFilters(), query.getGroups(), query.getGroupFilters(), query.getSorts(), query.getBundle(), boostList);
	}
	

	public GroupHit searchGroupHit(Query query) throws ClauseException, IOException, IRException {
		search(query.getMeta(), query.getClause(), query.getFilters(), query.getGroups(), null, null, null, null);
		return new GroupHit(makeGroupData(), totalCount);
	}

    @Deprecated
	public void search(Metadata meta, Clause clause, Filters filters, Groups groups, Filters groupFilters, Sorts sorts, Bundle bundle, PkScoreList boostList) throws ClauseException,
			IOException, IRException {
		FieldIndexesReader fieldIndexesReader = null;
		int sortMaxSize = meta.start() + meta.rows() - 1;
		
		int docCount = segmentReader.docCount();
		// Search
		OperatedClause operatedClause = null;
		
		if (clause == null) {
			operatedClause = new AllDocumentOperatedClause(docCount);
		} else {
			operatedClause = clause.getOperatedClause(docCount, segmentReader.newSearchIndexesReader(), highlightInfo);
		}
		//BOOST
		if(boostList != null) {
			// pk를 내부 docNo로 바뀐 opclause가 리턴된다.
			OperatedClause boostClause = new PkScoreOperatedClause("pk boost", boostList, segmentReader.newSearchIndexesReader());
			operatedClause = new BoostOperatedClause(operatedClause, boostClause);
		}
		// filter
		if (filters != null) {
			if(fieldIndexesReader == null){
				fieldIndexesReader = segmentReader.newFieldIndexesReader();
			}
			//schema를 통해 field index setting을 알아야 필터링시 ignorecase등의 정보를 활용가능하다.
			hitFilter = filters.getHitFilter(schema, fieldIndexesReader, BULK_SIZE);
		}

		//group
		if (groups != null) {
			if(fieldIndexesReader == null){
				fieldIndexesReader = segmentReader.newFieldIndexesReader();
			}
			groupGenerator = groups.getGroupDataGenerator(schema, segmentReader.newGroupIndexesReader(), fieldIndexesReader);
			if (groupFilters != null) {
				groupHitFilter = groupFilters.getHitFilter(schema, fieldIndexesReader, BULK_SIZE);
			}
		}

		// sort
		// Sorts sorts = q.getSorts();
		SortGenerator sortGenerator = null;
		if (sorts == null || sorts == Sorts.DEFAULT_SORTS) {
			ranker = new DefaultRanker(sortMaxSize);
			sortGenerator = new SortGenerator();
		} else {
			if(fieldIndexesReader == null){
				fieldIndexesReader = segmentReader.newFieldIndexesReader();
			}
			// ranker에 정렬 로직이 담겨있다.
			// ranker 안에는 필드타입과 정렬옵션을 확인하여 적합한 byte[] 비교를 수행한다.
			ranker = sorts.createRanker(schema, sortMaxSize);
			sortGenerator = sorts.getSortGenerator(schema, fieldIndexesReader, bundle);
		}

		RankInfo[] rankInfoList = new RankInfo[BULK_SIZE];
		boolean exausted = false;
		BitSet localDeleteSet = segmentReader.deleteSet();


		/**
		 * Explanation 객체들.
		 */
		ClauseExplanation clauseExplanation = null;
		boolean isExplain = meta.isSearchOption(Query.SEARCH_OPT_EXPLAIN);
		if(isExplain) {
			explanation = new Explanation();
			clauseExplanation = explanation.createClauseExplanation();
		}
		
		if (logger.isTraceEnabled() && operatedClause != null) {
            StringWriter writer = new StringWriter();
            writer.append("\n");
            operatedClause.printTrace(writer, 4, 0);
            logger.trace("SegmentSearcher[seg#{}] stack >> \n{}", segmentReader.segmentInfo().getId(), writer.toString());
		}
		
		
		operatedClause.init(clauseExplanation);
//		int searchTime = 0, sortTime = 0, groupTime = 0, filterTime = 0;
		while (!exausted) {
			int nread = 0;
//			long st = System.nanoTime();
			// search
			for (nread = 0; nread < BULK_SIZE; nread++) {
				RankInfo rankInfo = new RankInfo(isExplain);
				if (operatedClause.next(rankInfo)) {
					rankInfoList[nread] = rankInfo;
				} else {
					exausted = true;
					break;
				}
			}
//			searchTime += (System.nanoTime() - st);st = System.nanoTime();
			if (filters != null && filters.size() > 0 && hitFilter != null) {
				nread = hitFilter.filtering(rankInfoList, nread);
			}
//			filterTime += (System.nanoTime() - st);st = System.nanoTime();
			if (!exausted && nread == 0) {
				continue;
			}

			// check delete documents
			int count = 0;
			for (int i = 0; i < nread; i++) {
				RankInfo rankInfo = rankInfoList[i];
				// Check deleted list
				if (!localDeleteSet.isSet(rankInfo.docNo())) {
					rankInfoList[count] = rankInfo;
					count++;
//					logger.debug("ok docNo = {}", rankInfo.docNo());
				} else {
//					logger.debug("deleted docNo = {}", rankInfo.docNo());
				}

			}
//			logger.debug("check delete docs {} => {}", nread, count);
			nread = count;
//			st = System.nanoTime();
			// group
			if (groups != null) {
				groupGenerator.insert(rankInfoList, nread);
				// group filter
				if (groupFilters != null) {
					nread = groupHitFilter.filtering(rankInfoList, nread);
				}
			}

            HitElement[] e = sortGenerator.getHitElement(rankInfoList, nread);
            for (int i = 0; i < nread; i++) {
                if(ranker.push(e[i])) {
                    totalCount++;
                }
            }
			
			
//			sortTime += (System.nanoTime() - st);
//			totalCount += nread;
		}
		
//		logger.debug("clauseExplanation >> {}\n{}",isExplain, clauseExplanation);

//		 logger.debug("#### time = se:{}ms, ft:{}ms, gr:{}ms, so:{}ms", searchTime / 1000000, filterTime / 1000000, groupTime / 1000000, sortTime / 1000000);
	}
	
	private HitReader searchHitReader(Metadata meta, Clause clause, Filters filters, Groups groups, Filters groupFilters, Sorts sorts, Bundle bundle, PkScoreList boostList) throws ClauseException,
	IOException, IRException {
		return new HitReader(segmentReader, meta, clause, filters, groups, groupFilters, sorts, bundle, boostList);
	}

	public Hit searchIndex(Clause clause, Sorts sorts, int start, int length) throws ClauseException,
		IOException, IRException {
		return searchIndex(clause, sorts, start, length, null);
	}
	
	/**
	 * @param docFilter 결과 문서를 제한하기 위한 필터로써, 한번 검색된 필터를 가지고 있다가 전달해주면, 이 필터내의 문서에서만 결과를 만들도록 한다.
	 * 
	 * */
	public Hit searchIndex(Clause clause, Sorts sorts, int start, int length, BitSet docFilter) throws ClauseException,
		IOException, IRException {
		
		int totalCount = 0;
		FieldIndexesReader fieldIndexesReader = null;
		int sortMaxSize = start + length - 1;
		
		OperatedClause operatedClause = clause.getOperatedClause(0, segmentReader.newSearchIndexesReader(), null);
		// sort
		FixedMaxPriorityQueue<HitElement> ranker = null;
		 SortGenerator sortGenerator = null;
		if (sorts == null || sorts == Sorts.DEFAULT_SORTS) {
			ranker = new DefaultRanker(sortMaxSize);
			sortGenerator = new SortGenerator();
		} else {
			if(fieldIndexesReader == null){
				fieldIndexesReader = segmentReader.newFieldIndexesReader();
			}

			// ranker에 정렬 로직이 담겨있다.
			// ranker 안에는 필드타입과 정렬옵션을 확인하여 적합한 byte[] 비교를 수행한다.
			ranker = sorts.createRanker(schema, sortMaxSize);
			sortGenerator = sorts.getSortGenerator(schema, fieldIndexesReader, null);
		}
        
		
		RankInfo[] rankInfoList = new RankInfo[BULK_SIZE];
		boolean exausted = false;
		BitSet localDeleteSet = segmentReader.deleteSet();
		
		//int searchTime = 0, sortTime = 0, groupTime = 0, filterTime = 0;
		while (!exausted) {
			int nread = 0;
			// search
			for (nread = 0; nread < BULK_SIZE; nread++) {
				RankInfo rankInfo = new RankInfo();
				if (operatedClause.next(rankInfo)) {
					rankInfoList[nread] = rankInfo;
				} else {
					exausted = true;
					break;
				}
			}
			if (!exausted && nread == 0) {
				continue;
			}
		
			//
			//check filtered list
			//
			int count = 0;
			if(docFilter != null) {
				for (int i = 0; i < nread; i++) {
					RankInfo rankInfo = rankInfoList[i];
					// Check deleted list
					if (docFilter.isSet(rankInfo.docNo())) {
						rankInfoList[count] = rankInfo;
						count++;
//						logger.debug("ok docNo = {}", rankInfo.docNo());
					} else {
			//			logger.debug("deleted docNo = {}", rankInfo.docNo());
					}
			
				}
				nread = count;
			}
			
			// check delete documents
			count = 0;
			for (int i = 0; i < nread; i++) {
				RankInfo rankInfo = rankInfoList[i];
				// Check deleted list
				if (!localDeleteSet.isSet(rankInfo.docNo())) {
					rankInfoList[count] = rankInfo;
					count++;
		//			logger.debug("ok docNo = {}", rankInfo.docNo());
				} else {
		//			logger.debug("deleted docNo = {}", rankInfo.docNo());
				}
		
			}

			nread = count;

            HitElement[] e = sortGenerator.getHitElement(rankInfoList, nread);
            for (int i = 0; i < nread; i++) {
                ranker.push(e[i]);
            }

			totalCount += nread;
		}
		
		int segmentSequence = segmentReader.sequence();
		int size = ranker.size();
		FixedHitStack hitStack = new FixedHitStack(size);
		for (int i = 0; i < size; i++) {
			HitElement el = ranker.pop();
			//여기에서 segment번호를 셋팅해준다. 
			el.setDocNo(segmentSequence, el.docNo());
			hitStack.push(el);
		}
		
		return new Hit(hitStack, null, totalCount, null);
	}
	
	/**
	 * Top K개의 HIT들을 리턴한다.
	 * 
	 * @return
	 */
	private FixedHitStack rankHitList() {
		int segmentSequence = segmentReader.sequence();
		int size = ranker.size();
		FixedHitStack hitStack = new FixedHitStack(size);
		for (int i = 0; i < size; i++) {
			HitElement el = ranker.pop();
			
			//여기에서 segment번호를 셋팅해준다. 
			el.setDocNo(segmentSequence, el.docNo());
//			logger.debug("rank hit seg#{} {}:{} > {}", segmentSequence, el.docNo(), el.score(), el.rowExplanations());
			hitStack.push(el);
		}
		
		return hitStack;
	}

	// 그룹결과는 문서를 next로 다읽은 경우에 완료된다.
	private GroupsData makeGroupData() throws IOException {
		if (groupGenerator == null)
			return new GroupsData(null, totalCount);

		return groupGenerator.generate();
	}
	
	public Explanation explanation(){
		return explanation;
	}

}
