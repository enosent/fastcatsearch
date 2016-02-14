package org.fastcatsearch.ir.search;

import org.fastcatsearch.ir.common.IRException;
import org.fastcatsearch.ir.filter.FilterException;
import org.fastcatsearch.ir.group.GroupDataGenerator;
import org.fastcatsearch.ir.group.GroupsData;
import org.fastcatsearch.ir.io.BitSet;
import org.fastcatsearch.ir.query.*;
import org.fastcatsearch.ir.search.clause.*;
import org.fastcatsearch.ir.settings.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;

public class HitReader {
	private static Logger logger = LoggerFactory.getLogger(HitReader.class);
	
	private Schema schema;
	SegmentReader segmentReader;
	int segmentSequence;
	
	Filters filters;
	Groups groups;
	Filters groupFilters;
	Sorts sorts;
	
	OperatedClause operatedClause;
	FieldIndexesReader fieldIndexesReader;
	GroupDataGenerator groupGenerator;
	HitFilter hitFilter;
	private HitFilter groupHitFilter;
	SortGenerator sortGenerator;
	
	
	private HighlightInfo highlightInfo;
	private Explanation explanation;
	int BULK_SIZE = 100;
	boolean isExplain;
	BitSet localDeleteSet;
	boolean exausted;
	RankInfo[] rankInfoList;
	HitElement[] hitElementBuffer;
	int nread;
	int totalCount;
	
	
	public HitReader(SegmentReader segmentReader, Metadata meta, Clause clause, Filters filters, Groups groups, Filters groupFilters, Sorts sorts, Bundle bundle, PkScoreList boostList) throws IOException, ClauseException, IRException {
		
		this.filters = filters;
		this.groups = groups;
		this.groupFilters = groupFilters;
		this.sorts = sorts;
		
		FieldIndexesReader fieldIndexesReader = null;
//		int sortMaxSize = meta.start() + meta.rows() - 1;
		schema = segmentReader.schema();
		int docCount = segmentReader.docCount();
		segmentSequence = segmentReader.sequence();
		// Search
		highlightInfo = new HighlightInfo();
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
		if (sorts == null || sorts == Sorts.DEFAULT_SORTS) {
			if(bundle  != null) {
				if(fieldIndexesReader == null){
					fieldIndexesReader = segmentReader.newFieldIndexesReader();
				}
				sortGenerator = new BundleSortGenerator(bundle, null, schema, fieldIndexesReader);
			}
		} else {
			if(fieldIndexesReader == null){
				fieldIndexesReader = segmentReader.newFieldIndexesReader();
			}
			sortGenerator = sorts.getSortGenerator(schema, fieldIndexesReader, bundle);
		}

//		RankInfo[] rankInfoList = new RankInfo[BULK_SIZE];
		localDeleteSet = segmentReader.deleteSet();


		/**
		 * Explanation 객체들.
		 */
		ClauseExplanation clauseExplanation = null;
		isExplain = meta.isSearchOption(Query.SEARCH_OPT_EXPLAIN);
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
		
		rankInfoList = new RankInfo[BULK_SIZE];
		hitElementBuffer = new HitElement[BULK_SIZE];
	}
	
	public HitElement next() throws IOException, FilterException {
		while (nread == 0) {
			if(exausted) {
				return null;
			} else { 
				fill();
			}
		}
		HitElement e = hitElementBuffer[--nread];
		e.setSegmentSequence(segmentSequence);
		return e;
	}
	
	
	private void fill() throws IOException, FilterException {
		nread = 0;
		while (!exausted) {
			
			// search and check delete documents
			while (nread < BULK_SIZE) {
				RankInfo rankInfo = new RankInfo(isExplain);
				if (operatedClause.next(rankInfo)) {
					if (!localDeleteSet.isSet(rankInfo.docNo())) {
						rankInfoList[nread] = rankInfo;
						nread++;
					}
				} else {
					exausted = true;
					break;
				}
			}
			if (exausted) {
				if(nread == 0) {
					return;
				}else{
					//fill 루프탈출.
					break;
				}
			}
			
			if(nread > 0) {
				break;
			}
		}
			
		if (filters != null && filters.size() > 0 && hitFilter != null) {
			nread = hitFilter.filtering(rankInfoList, nread);
		}
		
		// group
		if (groups != null) {
			groupGenerator.insert(rankInfoList, nread);
			
			// group filter
			if (groupFilters != null) {
				nread = groupHitFilter.filtering(rankInfoList, nread);
			}
		}
		
		if (sorts == null || sorts == Sorts.DEFAULT_SORTS) {
			//정렬없는 묶음검색의 경우.
			if(sortGenerator != null) {
				sortGenerator.getHitElement(rankInfoList, hitElementBuffer, nread);
			} else {
				for (int i = 0; i < nread; i++) {
					hitElementBuffer[i] = new HitElement(rankInfoList[i].docNo(), rankInfoList[i].score(), rankInfoList[i].hit(), rankInfoList[i].rowExplanations());
                    hitElementBuffer[i].setDistance(rankInfoList[i].distance());
				}
			}
		} else {
			sortGenerator.getHitElement(rankInfoList, hitElementBuffer, nread);
		}
		
		
		totalCount += nread;
		
	}
	
	public int totalCount() { 
		return totalCount;
	}
	
	public Explanation explanation() {
		return explanation;
	}
	
	public HighlightInfo highlightInfo() {
		return highlightInfo;
	}
	
	public GroupsData makeGroupData() throws IOException {
		if (groupGenerator == null)
			return new GroupsData(null, totalCount);

		return groupGenerator.generate();
	}
}
