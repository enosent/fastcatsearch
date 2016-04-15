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

package org.fastcatsearch.ir.sort;

import org.apache.lucene.util.BytesRef;

public class NumericAscSortFunction extends SortFunction {

	public NumericAscSortFunction() {
		super();
	}

	public NumericAscSortFunction(boolean isShuffle) {
		super(isShuffle);
	}

	@Override
	public int compare(BytesRef one, BytesRef two) {
		int ret = one.compareNumberTo(two);
		if(isShuffle && ret == 0) {
			// asc_shuffle의 경우 asc 정렬된 값 내에서 동일한 값 중에서는 랜덤으로 정렬한다.
			if(r.nextBoolean()) {
				return 1;
			} else {
				return -1;
			}
		} else {
			return ret;
		}
	}
}
