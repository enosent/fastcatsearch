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

package org.fastcatsearch.ir.search.clause;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;

import org.fastcatsearch.ir.query.RankInfo;


/**
 * 외부에서 직접 문서번호정보를 넣어 절을 만든다.
 * @author sangwook.song
 *
 */
public class UserOperatedClause extends OperatedClause {
	
	private int pos;
	private int count;
	private int[] docs;
	private int[] weight;
	
	public UserOperatedClause(int count, int[] docs, int[] weight) {
		super(null);
		this.count = count;
		this.docs = docs;
		this.weight = weight;
	}

	protected boolean nextDoc(RankInfo docInfo) {
		if(pos < count){
			if(weight == null) {
				docInfo.init(docs[pos], 0);
			} else {
				docInfo.init(docs[pos], weight[pos]);
			}
			pos++;
			return true;
		}
		docInfo.init(-1, 0);
		return false;
	}

	@Override
	public void close() {
	}

    @Override
    public void printTrace(Writer writer, int indent, int depth) throws IOException {

    }

    @Override
	protected void initClause(boolean explain) {
	}


//	@Override
//	protected void initExplanation() {
//	}

}
