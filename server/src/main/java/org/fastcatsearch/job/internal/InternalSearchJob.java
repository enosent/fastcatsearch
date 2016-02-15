package org.fastcatsearch.job.internal;

import java.io.IOException;

import org.fastcatsearch.common.io.Streamable;
import org.fastcatsearch.error.SearchError;
import org.fastcatsearch.error.ServerErrorCode;
import org.fastcatsearch.exception.FastcatSearchException;
import org.fastcatsearch.ir.IRService;
import org.fastcatsearch.ir.io.DataInput;
import org.fastcatsearch.ir.io.DataOutput;
import org.fastcatsearch.ir.query.InternalSearchResult;
import org.fastcatsearch.ir.query.Metadata;
import org.fastcatsearch.ir.query.Query;
import org.fastcatsearch.ir.query.QueryModifier;
import org.fastcatsearch.ir.search.CollectionHandler;
import org.fastcatsearch.ir.search.CollectionSearcher;
import org.fastcatsearch.ir.search.HitElement;
import org.fastcatsearch.ir.search.PkScore;
import org.fastcatsearch.ir.search.PkScoreList;
import org.fastcatsearch.job.Job;
import org.fastcatsearch.query.QueryMap;
import org.fastcatsearch.query.QueryParser;
import org.fastcatsearch.service.ServiceManager;
import org.fastcatsearch.transport.vo.StreamableInternalSearchResult;

public class InternalSearchJob extends Job implements Streamable {
	private static final long serialVersionUID = 4998297114497342795L;
	private QueryMap queryMap;
	private boolean forMerging;
	
	public InternalSearchJob(){}
	
	public InternalSearchJob(QueryMap queryMap){
		this.queryMap = queryMap;
	}
	
	public InternalSearchJob(QueryMap queryMap, boolean forMerging){
		this.queryMap = queryMap;
		this.forMerging = forMerging;
	}
	
	@Override
	public JobResult doRun() throws FastcatSearchException {
		
		Query q = QueryParser.getInstance().parseQuery(queryMap);

		String collectionId = queryMap.collectionId();
		try {
			Metadata meta = q.getMeta();
			QueryModifier queryModifier = meta.queryModifier();
			//쿼리모디파이.
			if (queryModifier != null) {
				q = queryModifier.modify(collectionId, q);
				meta = q.getMeta();
			}
			logger.debug("q > {}", q);
			InternalSearchResult result = null;
			
			IRService irService = ServiceManager.getInstance().getService(IRService.class);
			
			//Not Exist in Cache
			if(result == null){
				CollectionHandler collectionHandler = irService.collectionHandler(collectionId);
				if(collectionHandler == null){
                    throw new SearchError(ServerErrorCode.COLLECTION_NOT_FOUND, collectionId);
				}
				Query boostQuery = q.getBoostQuery();
				PkScoreList pkScoreList = null;
				if(boostQuery != null) {
                    try {
                        String boostKeyword = boostQuery.getMeta().getUserData("KEYWORD");
                        pkScoreList = new PkScoreList(boostKeyword);
                        String boostCollectionId = boostQuery.getMeta().collectionId();
                        CollectionHandler boostCollectionHandler = irService.collectionHandler(boostCollectionId);
                        CollectionSearcher boostCollectionSearcher = boostCollectionHandler.searcher();
                        InternalSearchResult r = boostCollectionSearcher.searchInternal(boostQuery, forMerging);
                        for (HitElement e : r.getHitElementList()) {
                            if (e == null) {
                                continue;
                            }
                            //FIXME 첫번째 필드가 id이다.
                            logger.debug("e.docNo() > {}", e.docNo());
                            logger.debug("field > {}", boostCollectionSearcher.requestDocument(e.docNo()).get(1));
                            String id = boostCollectionSearcher.requestDocument(e.docNo()).get(1).toString();
                            int score = e.score();
                            pkScoreList.add(new PkScore(id, score));
                        }
                    } catch(Throwable t) {
                        logger.error("error while boosting query > " + boostQuery, t);
                    }
				}
				result = collectionHandler.searcher().searchInternal(q, forMerging, pkScoreList);
			}

			return new JobResult(new StreamableInternalSearchResult(result));

        } catch (SearchError e){
            throw e;
		} catch(Exception e){
			throw new FastcatSearchException(e);
		}

	}
	@Override
	public void readFrom(DataInput input) throws IOException {
		this.queryMap = new QueryMap();
		queryMap.readFrom(input);
		this.forMerging = input.readBoolean();
	}
	@Override
	public void writeTo(DataOutput output) throws IOException {
		queryMap.writeTo(output);
		output.writeBoolean(forMerging);
	}
}
