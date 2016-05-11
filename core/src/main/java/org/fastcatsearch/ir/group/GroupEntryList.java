/*
 * Copyright 2013 Websquared, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fastcatsearch.ir.group;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.fastcatsearch.ir.query.Group;

/**
 * 하나의 그룹핑결과를 담는다.
 * 결과는 function갯수만큼 GroupEntry내에 여러개의 grouping value들이 추가될수 있다.
 * */
public class GroupEntryList {
	private List<GroupEntry> entryList;
	private int totalCount; //검색결과 갯수이며, 그룹핑 결과갯수는 아닌다.
	
	public GroupEntryList(){ }
	
	public GroupEntryList(List<GroupEntry> e, int totalCount){
		this.entryList = e;
		this.totalCount = totalCount;
	}
	
	public GroupEntry getEntry(int i){
		return entryList.get(i);
	}
	
	public List<GroupEntry> getEntryList(){
		return entryList;
	}
	
	public int size(){
		if(entryList == null){
			return 0;
		}
		return entryList.size();
	}
	
	public int totalCount(){
		return totalCount;
	}
	
	public void setTotalCount(int totalCount){
		this.totalCount = totalCount;
	}
	public void add(GroupEntry groupEntry) {
		if(entryList == null){
			entryList = new ArrayList<GroupEntry>(); 
		}
		entryList.add(groupEntry);
	}

    /**
     * 키값이 NULL일 경우, 항상 마지막에 출현하도록.
     * */
    public static Comparator<GroupEntry> KeyAscendingComparator = new Comparator<GroupEntry>(){
        @Override
        public int compare(GroupEntry o1, GroupEntry o2) {
            if(o1.key == null || o2.key == null) {
                return 0;
            } else if(o1.key == null) {
                return 1;
            } else if(o2.key == null) {
                return -1;
            }
            return o1.key.compareTo(o2.key);
        }
    };
    public static Comparator<GroupEntry> KeyDescendingComparator = new Comparator<GroupEntry>(){
        @Override
        public int compare(GroupEntry o1, GroupEntry o2) {
            if(o1.key == null || o2.key == null) {
                return 0;
            } else if(o1.key == null) {
                return 1;
            } else if(o2.key == null) {
                return -1;
            }
            return o2.key.compareTo(o1.key);
        }
    };

    /**
     * Value값이 NULL일 경우, 0일수도 있으므로, 정렬순서에 맞게처리.
     * */
    public static Comparator<GroupEntry> ValueAscendingComparator = new Comparator<GroupEntry>(){
        @Override
        public int compare(GroupEntry o1, GroupEntry o2) {
            if(o1.groupingValue[0] == null && o2.groupingValue[0] == null) {
                return 0;
            } else if(o1.groupingValue[0] == null) {
                return -1;
            } else if(o2.groupingValue[0] == null) {
                return 1;
            }
            return o1.groupingValue[0].compareTo(o2.groupingValue[0]);
        }
    };
    public static Comparator<GroupEntry> ValueDescendingComparator = new Comparator<GroupEntry>(){
        @Override
        public int compare(GroupEntry o1, GroupEntry o2) {
            if(o1.groupingValue[0] == null && o2.groupingValue[0] == null) {
                return 0;
            } else if(o1.groupingValue[0] == null) {
                return 1;
            } else if(o2.groupingValue[0] == null) {
                return -1;
            }
            return o2.groupingValue[0].compareTo(o1.groupingValue[0]);
        }
    };

	public void sort(int sortOrder) {
		if(entryList == null){
			return;
		}
		
		if(sortOrder == Group.SORT_KEY_ASC){
			Collections.sort(entryList, GroupEntryList.KeyAscendingComparator);
		}else if(sortOrder == Group.SORT_KEY_DESC){
			Collections.sort(entryList, GroupEntryList.KeyDescendingComparator);
		}else if(sortOrder == Group.SORT_VALUE_ASC){
			Collections.sort(entryList, GroupEntryList.ValueAscendingComparator);
		}else if(sortOrder == Group.SORT_VALUE_DESC){
			Collections.sort(entryList, GroupEntryList.ValueDescendingComparator);
		}
	}
	
}
