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

package org.fastcatsearch.ir.query;

import java.util.ArrayList;
import java.util.List;

import org.fastcatsearch.ir.search.clause.TermOccurrences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RankInfo는 포스팅을 검색한 결과로 OperatedClause의 next메소드를 통해 만들어 진다. RankInfo는 docNo와
 * score정보만 가지고 있으며 SortGenerator를 통해 소트필드정보를 가지는 HitElement를 만들게 된다.
 * 
 * @author sangwook.song
 * 
 */
public class RankInfo {
	protected static Logger logger = LoggerFactory.getLogger(RankInfo.class);
	private static final List<TermOccurrences> EMPTY_TERM_OCCURRENCES = new ArrayList<TermOccurrences>(0);
	private int docNo;
	private int score;
	private int hit; // 매칭횟수.
    private List<TermOccurrences> termOccurrencesList;
	private int matchFlag;
	
	private boolean explain;
	private List<RowExplanation> rowExplanations;

    private float distance;

    public RankInfo() {
	}
	
	public RankInfo(boolean explain) {
		this.explain = explain;
	}

	public void init(int docNo, int score) {
		init(docNo, score, 1);
	}

    public void init(int docNo, int score, int hit) {
		this.docNo = docNo;
		this.score = score;
		this.hit = hit;
        this.matchFlag = 0;
	}

    public void init(RankInfo another) {
        this.docNo = another.docNo;
        this.score = another.score;
        this.hit = another.hit;
        this.matchFlag = another.matchFlag;
        explain(another);
        if(another.termOccurrencesList != null) {
            addTermOccurrencesList(another.termOccurrencesList);
        }
    }
    public void setEmpty() {
        this.docNo = -1;
        this.score = 0;
        this.hit = 0;
        this.matchFlag = 0;
    }

	public boolean isExplain(){
		return explain;
	}
	
	public int docNo() {
		return docNo;
	}

	public int score() {
		return score;
	}

	public int hit() {
		return hit;
	}

	public void addScore(int add) {
		score += add;
	}

	public void addHit(int add) {
		hit += add;
	}

	public void multiplyScore(float mul) {
		score *= mul;
	}

	public void score(int score) {
		this.score = score;
	}

	public void hit(int hit) {
		this.hit = hit;
	}

    public float distance() {
        return distance;
    }

    public void distance(float distance) {
        this.distance = distance;
    }
	public String toString() {
		return "docNo=" + docNo + ",score=" + score + ",hit=" + hit + ",distance=" + distance;
	}

	public int matchFlag() {
		return matchFlag;
	}

	//매칭된 위치를 or 해준다.
	//위치는 bit flag를 사용한다. Int는 총 32개 위치 사용가능.
	public void addMatchSequence(int sequence) {
		//부호비트 이상의 범위는 셋팅불가.
		if(sequence < 32) {
			matchFlag |= (1 << sequence);
		}
	}
	
	public void addMatchFlag(int flag) {
		matchFlag |= flag;
	}
	public boolean isMatchContains(int flag) {
		if(matchFlag == 0 || flag == 0){
			return false;
		}
		//둘의 OR 결과가 matchFlag 와 동일하다면 matchFlag가 flag를 모두 포함한 것이다.
		return (matchFlag | flag) == matchFlag;
	}
	
	public List<RowExplanation> rowExplanations(){
		return rowExplanations;
	}
	
	public void explain(String id, int score, String description) {
		if(rowExplanations == null){
			rowExplanations = new ArrayList<RowExplanation>();
		}
		
		rowExplanations.add(new RowExplanation(id, score, description));
	}
	
	public void explain(RankInfo rankInfo) {
		if(rankInfo.rowExplanations() != null){
			if(rowExplanations == null){
				rowExplanations = new ArrayList<RowExplanation>();
			}
			for(RowExplanation exp : rankInfo.rowExplanations()){
				rowExplanations.add(exp);
			}
		}
	}

	public void reset() {
		if(rowExplanations != null){
			rowExplanations.clear();
		}
	}

    public void addTermOccurrences(TermOccurrences termOccurs) {
        if(termOccurrencesList == null) {
            termOccurrencesList = new ArrayList<TermOccurrences>();
        }
        termOccurrencesList.add(termOccurs);
    }

    public void addTermOccurrencesList(List<TermOccurrences> termOccursList) {
        if(termOccurrencesList == null) {
            termOccurrencesList = new ArrayList<TermOccurrences>();
        }
        this.termOccurrencesList.addAll(termOccursList);
    }
    public List<TermOccurrences> getTermOccurrencesList() {
        return termOccurrencesList != null ? termOccurrencesList : EMPTY_TERM_OCCURRENCES;
    }

    public void clearOccurrence() {
        if(termOccurrencesList != null) {
            termOccurrencesList.clear();
        }
    }
}
