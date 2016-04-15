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

import java.util.Random;

/**
 * 정렬 결과는 역순으로 사용된다.
 * */
public abstract class SortFunction {
	protected Random r;
	protected boolean isShuffle;

	public SortFunction() {
		r = new Random();
		this.isShuffle = false;
	}

	public SortFunction(boolean isShuffle) {
		// shuffle 값을 지정하여 초기화할 경우
		r = new Random();
		this.isShuffle = isShuffle;
	}

	public abstract int compare(BytesRef one, BytesRef two);
}
