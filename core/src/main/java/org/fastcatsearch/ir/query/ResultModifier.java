package org.fastcatsearch.ir.query;

import org.fastcatsearch.ir.common.IRException;

public abstract class ResultModifier {

	public ResultModifier() {}

	public abstract Result modify(Result result) throws IRException;

	public abstract Result modify(Result result, String keyword, String collection, String htTag) throws IRException;

}
