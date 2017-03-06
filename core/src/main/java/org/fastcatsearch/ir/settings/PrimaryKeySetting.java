package org.fastcatsearch.ir.settings;

import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;


public class PrimaryKeySetting {
	public static final String ID = "_PK";
	private String id;
	private List<RefSetting> fieldList;

	public PrimaryKeySetting() {}
	
	public PrimaryKeySetting(String id) {
		this.id = id.toUpperCase();
	}

    @XmlAttribute(name = "id", required = false)
    public String getId() {
//		return id;
		return ID;
	}

	public void setId(String id) {
		this.id = id.toUpperCase();
	}
	
	@XmlElement(name="field")
	public List<RefSetting> getFieldList() {
		return fieldList;
	}

	public void setFieldList(List<RefSetting> fieldList) {
		this.fieldList = fieldList;
	}
	
}
