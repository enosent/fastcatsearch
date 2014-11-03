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

package org.fastcatsearch.ir.util;

import org.fastcatsearch.ir.common.IndexFileNames;
import org.fastcatsearch.ir.io.BufferedFileInput;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * .lexicon 파일에서 텀과 색인포지션정보를 stdout으로 봅아낸다.
 * 사용법은 java LexiconChecker <검색필드명> <세그먼트경로> <리비전번호>
 */
public class LexiconChecker {
	public static void main(String[] args) throws IOException {
		String id = args[0];
		File dir = new File(args[1]);
		int rev = Integer.parseInt(args[2]);
		LexiconChecker checker = new LexiconChecker(id, dir, rev);
		checker.list(System.out);
		checker.close();
	}
	
	BufferedFileInput indexInput;
	
	public LexiconChecker(String id, File dir, int revision) throws IOException{
		System.out.println("Check dir = "+dir.getAbsolutePath());

		indexInput = new BufferedFileInput(IndexFileNames.getRevisionDir(dir, revision), IndexFileNames.getSearchLexiconFileName(id));
	}
	
	public void close() throws IOException{
		indexInput.close();
	}

	public void list(PrintStream output) throws IOException{
        int indexSize = indexInput.readInt();
        output.println("Memory indexsize = "+indexSize);
        for (int k = 0; k < indexSize; k++) {
            output.println("word="+new String(indexInput.readUString())+" ,"+ indexInput.readLong());
        }

	}
}


