/*
 * Copyright (c) 2013 Websquared, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     swsong - initial API and implementation
 */

package org.fastcatsearch.job;

import java.io.File;

import org.fastcatsearch.common.io.Streamable;
import org.fastcatsearch.exception.FastcatSearchException;
import org.fastcatsearch.ir.ShardIndexer;
import org.fastcatsearch.ir.IRService;
import org.fastcatsearch.ir.common.IndexingType;
import org.fastcatsearch.ir.config.CollectionContext;
import org.fastcatsearch.ir.config.ShardContext;
import org.fastcatsearch.ir.config.DataInfo.RevisionInfo;
import org.fastcatsearch.ir.config.DataInfo.SegmentInfo;
import org.fastcatsearch.ir.index.DeleteIdSet;
import org.fastcatsearch.ir.search.CollectionHandler;
import org.fastcatsearch.ir.search.ShardHandler;
import org.fastcatsearch.ir.util.Formatter;
import org.fastcatsearch.job.result.IndexingJobResult;
import org.fastcatsearch.service.ServiceManager;
import org.fastcatsearch.transport.vo.StreamableThrowable;
import org.fastcatsearch.util.CollectionContextUtil;
import org.fastcatsearch.util.ShardContextUtil;

public class ShardAddIndexingJob extends IndexingJob {

	private static final long serialVersionUID = -2307892463724479369L;
	
	@Override
	public JobResult doRun() throws FastcatSearchException {
		prepare(IndexingType.ADD);
		
		updateIndexingStatusStart();
		
		boolean isSuccess = false;
		Object result = null;
		Throwable throwable = null;
		
		try {
			IRService irService = ServiceManager.getInstance().getService(IRService.class);
			
			CollectionHandler collectionHandler = irService.collectionHandler(collectionId);
			if(collectionHandler == null){
				indexingLogger.error("[{}] CollectionHandler is not running!", collectionId);
				throw new FastcatSearchException("## ["+collectionId+"] CollectionHandler is not running...");
			}
			ShardHandler shardHandler = collectionHandler.getShardHandler(shardId);
			if(shardHandler == null){
				indexingLogger.error("[{}] ShardHandler is not running!", shardId);
				throw new FastcatSearchException("## ["+shardId+"] ShardHandler is not running...");
			}
			/*
			 * Do indexing!!
			 */
			//////////////////////////////////////////////////////////////////////////////////////////
			CollectionContext collectionContext = irService.collectionContext(collectionId).copy();
			ShardContext shardContext = collectionContext.getShardContext(shardId);
			
			ShardIndexer shardIndexer = new ShardIndexer(shardContext);
			SegmentInfo segmentInfo = shardIndexer.addIndexing(shardHandler);
			RevisionInfo revisionInfo = segmentInfo.getRevisionInfo();
			//////////////////////////////////////////////////////////////////////////////////////////
			
			logger.debug("색인후 segmentInfo >> {}", segmentInfo);
			logger.debug("색인후 revisionInfo >> {}", revisionInfo);
			
			if(revisionInfo.getInsertCount() == 0 && revisionInfo.getDeleteCount() == 0){
				int duration = (int) (System.currentTimeMillis() - indexingStartTime());
				result = new IndexingJobResult(collectionId, shardId, revisionInfo, duration);
				isSuccess = true;

				return new JobResult(result);
			}
			
			
			collectionContext.updateCollectionStatus(IndexingType.ADD, revisionInfo, indexingStartTime(), System.currentTimeMillis());
			
			File segmentDir = collectionContext.indexFilePaths().segmentFile(shardContext.getDataSequence(), segmentInfo.getId());
			DeleteIdSet deleteIdSet = shardIndexer.deleteIdSet();
			shardHandler.updateShard(shardContext, segmentInfo, segmentDir, deleteIdSet);
			
			//저장.
			ShardContextUtil.saveAfterIndexing(shardContext);
			
			
			int duration = (int) (System.currentTimeMillis() - indexingStartTime());
			
			//캐시 클리어.
			getJobExecutor().offer(new CacheServiceRestartJob());
			
			indexingLogger.info("[{}] Incremental Indexing Finished! revisionInfo={}, time = {}", collectionId, revisionInfo, Formatter.getFormatTime(duration));
			logger.info("== SegmentStatus ==");
			shardHandler.printSegmentStatus();
			logger.info("===================");
			
			result = new IndexingJobResult(collectionId, shardId, revisionInfo, duration);
			isSuccess = true;

			return new JobResult(result);
			
		} catch (Throwable e) {
			indexingLogger.error("[" + collectionId + "] Indexing", e);
			throwable = e;
			throw new FastcatSearchException("ERR-00501", throwable, collectionId);
		} finally {
			Streamable streamableResult = null;
			if (throwable != null) {
				streamableResult = new StreamableThrowable(throwable);
			} else if (result instanceof IndexingJobResult) {
				streamableResult = (IndexingJobResult) result;
			}
			
			updateIndexingStatusFinish(isSuccess, streamableResult);
		}
		
		
		
	}

}
