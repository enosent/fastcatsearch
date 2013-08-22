package org.fastcatsearch.util;

import java.io.File;

import javax.xml.bind.JAXBException;

import org.fastcatsearch.env.Path;
import org.fastcatsearch.ir.common.SettingException;
import org.fastcatsearch.ir.config.ClusterConfig;
import org.fastcatsearch.ir.config.CollectionConfig;
import org.fastcatsearch.ir.config.CollectionContext;
import org.fastcatsearch.ir.config.CollectionIndexStatus;
import org.fastcatsearch.ir.config.ShardIndexStatus;
import org.fastcatsearch.ir.config.CollectionsConfig.Collection;
import org.fastcatsearch.ir.config.DataInfo;
import org.fastcatsearch.ir.config.DataInfo.RevisionInfo;
import org.fastcatsearch.ir.config.DataInfo.SegmentInfo;
import org.fastcatsearch.ir.config.DataSourceConfig;
import org.fastcatsearch.ir.config.SingleSourceConfig;
import org.fastcatsearch.ir.config.JAXBConfigs;
import org.fastcatsearch.ir.settings.Schema;
import org.fastcatsearch.ir.settings.SchemaSetting;
import org.fastcatsearch.settings.SettingFileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollectionContextUtil {
	private static final Logger logger = LoggerFactory.getLogger(CollectionContextUtil.class);

	public static CollectionContext init(IndexFilePaths collectionFilePaths) throws SettingException {
		try {
			Path collectionDir = new Path(collectionFilePaths.file());
			SchemaSetting schemaSetting = new SchemaSetting();
			JAXBConfigs.writeConfig(collectionDir.file(SettingFileNames.schema), schemaSetting, SchemaSetting.class);
			CollectionConfig collectionConfig = new CollectionConfig();
			JAXBConfigs.writeConfig(collectionDir.file(SettingFileNames.collectionConfig), collectionConfig, CollectionConfig.class);
			ClusterConfig clusterConfig = new ClusterConfig();
			JAXBConfigs.writeConfig(collectionDir.file(SettingFileNames.clusterConfig), clusterConfig, ClusterConfig.class);
			DataSourceConfig dataSourceConfig = new DataSourceConfig();
			JAXBConfigs.writeConfig(collectionDir.file(SettingFileNames.datasourceConfig), dataSourceConfig, SingleSourceConfig.class);
			ShardIndexStatus collectionStatus = new ShardIndexStatus();
			JAXBConfigs.writeConfig(collectionDir.file(SettingFileNames.collectionStatus), collectionStatus, ShardIndexStatus.class);
//			DataInfo dataInfo = new DataInfo();
//			JAXBConfigs.writeConfig(new File(collectionFilePaths.dataFile(0), SettingFileNames.dataInfo), dataInfo, DataInfo.class);
			Schema schema = new Schema(schemaSetting);
			CollectionContext collectionContext = new CollectionContext(collectionFilePaths.getId(), collectionFilePaths);
			collectionContext.init(schema, null, collectionConfig, clusterConfig, dataSourceConfig, collectionStatus);
			return collectionContext;
		} catch (Exception e) {
			throw new SettingException("CollectionContext 로드중 에러발생", e);
		}
	}

	public static CollectionContext load(Collection collection, IndexFilePaths indexFilePaths, Integer dataSequence) throws SettingException {
		try {
			Path collectionDir = new Path(indexFilePaths.file());
			File schemaFile = collectionDir.file(SettingFileNames.schema);
			SchemaSetting schemaSetting = JAXBConfigs.readConfig(schemaFile, SchemaSetting.class);
			File workSchemaFile = collectionDir.file(SettingFileNames.workSchema);
			SchemaSetting workSchemaSetting = JAXBConfigs.readConfig(workSchemaFile, SchemaSetting.class);
			CollectionConfig collectionConfig = JAXBConfigs.readConfig(collectionDir.file(SettingFileNames.collectionConfig), CollectionConfig.class);
			ClusterConfig clusterConfig = JAXBConfigs.readConfig(collectionDir.file(SettingFileNames.clusterConfig), ClusterConfig.class);
			File dataSourceConfigFile = collectionDir.file(SettingFileNames.datasourceConfig);
			DataSourceConfig dataSourceConfig = null;
			if (dataSourceConfigFile.exists()) {
				dataSourceConfig = JAXBConfigs.readConfig(dataSourceConfigFile, DataSourceConfig.class);
			} else {
				dataSourceConfig = new DataSourceConfig();
			}

			File collectionStatusFile = collectionDir.file(SettingFileNames.collectionStatus);
			CollectionIndexStatus collectionStatus = JAXBConfigs.readConfig(collectionStatusFile, CollectionIndexStatus.class);

//			if (dataSequence == null) {
//				// dataSequence가 없으므로 indexedSequence로 선택하여 로딩한다.
//				int indexedSequence = collectionStatus.getSequence();
//				dataSequence = indexedSequence;
//			}

			// dataSequence가 null아 아니면 원하는 sequence의 정보를 읽어온다.
			File dataDir = indexFilePaths.dataFile();
			if (!dataDir.exists()) {
				dataDir.mkdirs();
			}
//			File infoFile = new File(dataDir, SettingFileNames.dataInfo);
//			DataInfo dataInfo = null;
//			if (infoFile.exists()) {
//				dataInfo = JAXBConfigs.readConfig(infoFile, DataInfo.class);
//			} else {
//				logger.info("File not found : {}", infoFile);
//				dataInfo = new DataInfo();
//				JAXBConfigs.writeConfig(infoFile, dataInfo, DataInfo.class);
//			}
//
//			logger.debug("dataInfo.getSegmentInfoList() >> {}", dataInfo.getSegmentInfoList().size());
			if (!collectionStatus.isEmpty()) {
				// SegmentInfoList가 없다면 data디렉토리를 지웠거나 색인이 안된상태이므로, 확인차 status초기화해준다.
				collectionStatus.clear();
				JAXBConfigs.writeConfig(collectionStatusFile, collectionStatus, ShardIndexStatus.class);
			}

			Schema schema = new Schema(schemaSetting);
			Schema workSchema = null;
			if (workSchemaSetting != null) {
				workSchema = new Schema(workSchemaSetting);
			}
			CollectionContext collectionContext = new CollectionContext(indexFilePaths.getId(), indexFilePaths);
			collectionContext.init(schema, workSchema, collectionConfig, clusterConfig, dataSourceConfig, collectionStatus);
			return collectionContext;
		} catch (Exception e) {
			throw new SettingException("CollectionContext 로드중 에러발생", e);
		}
	}

	public static void write(CollectionContext collectionContext) throws SettingException {
		try {
			IndexFilePaths collectionFilePaths = collectionContext.indexFilePaths();

			Schema schema = collectionContext.schema();
			Schema workSchema = collectionContext.workSchema();
			CollectionConfig collectionConfig = collectionContext.collectionConfig();
			CollectionIndexStatus collectionStatus = collectionContext.collectionStatus();
//			DataInfo dataInfo = collectionContext.dataInfo();
			DataSourceConfig dataSourceConfig = collectionContext.dataSourceConfig();

			File collectionDir = collectionFilePaths.file();

			if (schema != null && schema.schemaSetting() != null) {
				SchemaSetting schemaSetting = schema.schemaSetting();
				JAXBConfigs.writeConfig(new File(collectionDir, SettingFileNames.schema), schemaSetting, SchemaSetting.class);
			}
			if (workSchema != null && workSchema.schemaSetting() != null) {
				SchemaSetting schemaSetting = schema.schemaSetting();
				JAXBConfigs.writeConfig(new File(collectionDir, SettingFileNames.workSchema), schemaSetting, SchemaSetting.class);
			}
			if (collectionConfig != null) {
				JAXBConfigs.writeConfig(new File(collectionDir, SettingFileNames.collectionConfig), collectionConfig, CollectionConfig.class);
			}
			if (collectionStatus != null) {
				JAXBConfigs.writeConfig(new File(collectionDir, SettingFileNames.collectionStatus), collectionStatus, ShardIndexStatus.class);
			}
//			if (dataInfo != null) {
//				File dataDir = collectionFilePaths.dataFile(collectionStatus.getSequence());
//				dataDir.mkdirs();
//				JAXBConfigs.writeConfig(new File(dataDir, SettingFileNames.dataInfo), dataInfo, DataInfo.class);
//			}

			if (dataSourceConfig != null) {
				JAXBConfigs.writeConfig(new File(collectionDir, SettingFileNames.datasourceConfig), dataSourceConfig, DataSourceConfig.class);
			}
		} catch (Exception e) {
			throw new SettingException("CollectionContext 저장중 에러발생", e);
		}

	}

	/**
	 * status.xml 저장.
	 * data#//info.xml 저장.
	 * data#/{revision}/revision.xml 저장.
	 * 
	 * */
	public static void saveAfterIndexing(CollectionContext collectionContext) throws SettingException {
		IndexFilePaths collectionFilePaths = collectionContext.indexFilePaths();

		CollectionIndexStatus collectionStatus = collectionContext.collectionStatus();
//		DataInfo dataInfo = collectionContext.dataInfo();

		File collectionDir = collectionFilePaths.file();

		try {
			if (collectionStatus != null) {
				JAXBConfigs.writeConfig(new File(collectionDir, SettingFileNames.collectionStatus), collectionStatus, ShardIndexStatus.class);
			}
//			if (dataInfo != null) {
//				File dataDir = collectionFilePaths.dataFile(collectionStatus.getSequence());
//				dataDir.mkdirs();
//				logger.debug("Save DataInfo >> {}", dataInfo);
//				JAXBConfigs.writeConfig(new File(dataDir, SettingFileNames.dataInfo), dataInfo, DataInfo.class);
//
//				SegmentInfo lastSegmentInfo = dataInfo.getLastSegmentInfo();
//				File revisionDir = collectionFilePaths.revisionFile(collectionStatus.getSequence(), lastSegmentInfo.getId(),
//						lastSegmentInfo.getRevision());
//				RevisionInfo revisionInfo = lastSegmentInfo.getRevisionInfo();
//				if (revisionInfo != null) {
//					logger.debug("Save RevisionInfo >> {}, {}", revisionDir.getAbsolutePath(), revisionInfo);
//					JAXBConfigs.writeConfig(new File(revisionDir, SettingFileNames.revisionInfo), revisionInfo, RevisionInfo.class);
//				}
//			}
		} catch (JAXBException e) {
			throw new SettingException("색인후 CollectionContext 저장중 에러발생", e);
		}
	}

	// 색인이 끝나고 dataInfo 저장.
	// public static void saveDataInfo(CollectionContext collectionContext) throws SettingException {
	// CollectionFilePaths collectionFilePaths = collectionContext.collectionFilePaths();
	// DataInfo dataInfo = collectionContext.dataInfo();
	// CollectionStatus collectionStatus = collectionContext.collectionStatus();
	//
	// try {
	// if (dataInfo != null) {
	// logger.debug("Save DataInfo >> {}", dataInfo);
	// File dataDir = collectionFilePaths.dataFile(collectionStatus.getSequence());
	// JAXBConfigs.writeConfig(new File(dataDir, SettingFileNames.dataInfo), dataInfo, DataInfo.class);
	//
	// SegmentInfo lastSegmentInfo = dataInfo.getLastSegmentInfo();
	// File revisionDir = collectionFilePaths.revisionFile(collectionStatus.getSequence(), lastSegmentInfo.getId(),
	// lastSegmentInfo.getRevision());
	// RevisionInfo revisionInfo = lastSegmentInfo.getRevisionInfo();
	// if (revisionInfo != null) {
	// logger.debug("Save RevisionInfo >> {}", revisionInfo);
	// JAXBConfigs.writeConfig(new File(revisionDir, SettingFileNames.revisionInfo), revisionInfo, RevisionInfo.class);
	// }
	// }
	//
	// } catch (JAXBException e) {
	// throw new SettingException("CollectionContext 저장중 에러발생", e);
	// }
	// }

	// 색인끝나고 sequence 및 last 색인건수 저장.
	// public static void saveCollectionStatus(CollectionContext collectionContext) {
	// CollectionFilePaths collectionFilePaths = collectionContext.collectionFilePaths();
	// CollectionStatus collectionStatus = collectionContext.collectionStatus();
	// File collectionDir = collectionFilePaths.file();
	//
	// if (collectionStatus != null) {
	// logger.debug("Save CollectionStatus >> {}", collectionStatus);
	// JAXBConfigs.writeConfig(new File(collectionDir, SettingFileNames.collectionStatus), collectionStatus,
	// CollectionStatus.class);
	// }
	// }

	// workschema파일이 존재한다면 workschema를 schema로 대치하고
	// schema파일을 저장하고, workschema파일을 지운다.
	public static void applyWorkSchema(CollectionContext collectionContext) throws SettingException {
		IndexFilePaths collectionFilePaths = collectionContext.indexFilePaths();
		Schema schema = collectionContext.schema();
		Schema workSchema = collectionContext.workSchema();
		File collectionDir = collectionFilePaths.file();

		try {
			logger.debug("applyWorkSchema schema={}", schema);
			logger.debug("applyWorkSchema workSchema={}", workSchema);
			if (workSchema != null && !workSchema.isEmpty()) {
				schema.update(workSchema);
				collectionContext.setWorkSchema(null);
				JAXBConfigs.writeConfig(new File(collectionDir, SettingFileNames.schema), schema, Schema.class);
				File workSchemaFile = new File(collectionDir, SettingFileNames.workSchema);
				if (workSchemaFile.exists()) {
					workSchemaFile.delete();
				}
			}
		} catch (JAXBException e) {
			throw new SettingException("WorkSchema 적용중 에러발생", e);
		}

	}
}
