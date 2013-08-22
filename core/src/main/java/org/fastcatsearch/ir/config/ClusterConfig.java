package org.fastcatsearch.ir.config;

import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
<cluster-config>
	<shard id="vol1">
		<index-node>node1</index-node>
		<data-node>
			<node>node1</node>
			<node>node2</node>
		</data-node>
	</shard>

	<shard id="vol2">
		<index-node>node1</index-node>
		<data-node>
			<node>node1</node>
			<node>node2</node>
		</data-node>
	</shard>
</cluster-config>
*/

@XmlRootElement(name = "cluster-config")
public class ClusterConfig {
	private List<ShardClusterConfig> shardList;
	
	@XmlElement(name="shard")
	public List<ShardClusterConfig> getShardList() {
		return shardList;
	}

	public void setShardList(List<ShardClusterConfig> shardList) {
		this.shardList = shardList;
	}

	public static class ShardClusterConfig {
		private String id;
		private String indexNode;
		private List<String> dataNodeList;
		
		@XmlAttribute
		public String getId() {
			return id;
		}
		public void setId(String id) {
			this.id = id;
		}
		@XmlElement(name="index-node")
		public String getIndexNode() {
			return indexNode;
		}
		public void setIndexNode(String indexNode) {
			this.indexNode = indexNode;
		}
		
		@XmlElementWrapper(name="data-node")
		@XmlElement(name="node")
		public List<String> getDataNodeList() {
			return dataNodeList;
		}
		public void setDataNodeList(List<String> dataNodeList) {
			this.dataNodeList = dataNodeList;
		}
		
		
	}
}
