package org.fastcatsearch.ir.query;

import org.fastcatsearch.ir.common.IRException;

public abstract class ResultModifier {

	public ResultModifier() {
	}

	public abstract Result modify(Result result) throws IRException;

	/*
	* 2016-05-27 전제현
	* 결과모디파이어 작성 시 필요한 키워드, 컬렉션명, 하이라이팅 태그을 받는 modify 함수를 별도로 정의한다.
	* */
	public abstract Result modify(Result result, String keyword, String collection, String htTag) throws IRException;

}
