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

package org.fastcatsearch.query;

import org.fastcatsearch.error.ServerErrorCode;
import org.fastcatsearch.error.SearchError;
import org.fastcatsearch.ir.group.GroupFunction;
import org.fastcatsearch.ir.group.GroupFunctionType;
import org.fastcatsearch.ir.query.*;
import org.fastcatsearch.ir.query.Term.Option;
import org.fastcatsearch.ir.search.StoredProcedure;
import org.fastcatsearch.ir.search.clause.Clause;
import org.fastcatsearch.util.DynamicClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * 
 * 
 * @author sangwook.song
 * 
 */

public class QueryParser {

	private static Logger logger = LoggerFactory.getLogger(QueryParser.class);

	private static final String PLUGIN_PACKAGE_PATH = "org.fastcatsearch.ir.group.function";
	private static final String PLUGIN_CLASSNAME_SUFFIX = "GroupFunction";

	private static QueryParser instance = new QueryParser();

	private static final String SECTION_SEPARATOR = "(?<!\\\\)&";
    private static final String VALUE_SEPARATOR = "(?<!\\\\)=";
    private static final String COMMA_SEPARATOR = "(?<!\\\\),";
    private static final String COLON_SEPARATOR = "(?<!\\\\):";
    private static final String SEMICOLON_SEPARATOR = "(?<!\\\\);";
    private static final String RANGE_SEPARATOR = "(?<!\\\\)~";

	private QueryParser() {
	}

	public static QueryParser getInstance() {
		return instance;
	}

	// 검색 term 에서 사용
	private String removeEscape(String str) {
		return str.replaceAll("\\\\:", ":").replaceAll("\\\\&", "&").replaceAll("\\\\=", "=").replaceAll("\\\\,", ",");
	}

	// 필터 값에서 사용.
	// 필터는 여러 값으로 필터링이 가능해야 하므로 ; 구분자를 사용한다.
	// 그리고 범위지정에 ~를 사용한다.
	private void removeEscape(String[] strList) {
		for (int i = 0; i < strList.length; i++)
			strList[i] = strList[i].replaceAll("\\\\:", ":").replaceAll("\\\\&", "&").replaceAll("\\\\=", "=").replaceAll("\\\\,", ",")
					.replaceAll("\\\\;", ";").replaceAll("\\\\~", "~");
	}

	public Query parseQuery(QueryMap queryMap) {
        Query query = new Query();

        Iterator<Entry<String, String>> iterator = queryMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, String> entry = iterator.next();
            String key = entry.getKey();
            String value = entry.getValue().trim();
            if (value != null) {
                fillQuery(query, key, value);
            }
        }
        return query;

	}

	public Query.EL detectElement(String name) {
		try {
			//EL enum이 소문자이므로 소문자로 바꾼다.
			return Query.EL.valueOf(name.toLowerCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private void fillQuery(Query query, String key, String value) {
		Query.EL el = detectElement(key);
		if (el == null || value.length() == 0) {
			return;
		}
		logger.debug("fill {} >> {}", key, value);

		if (Query.EL.cn == el) {
			Metadata m = query.getMeta();
			m.setCollectionId(value);
		} else if (Query.EL.ht == el) {
			Metadata m1 = query.getMeta();
			String tags[] = value.split(COLON_SEPARATOR);
            if(tags.length != 2) {
                throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Highlighting tag must contains start and end tag with colon. : " + value);
            }
			removeEscape(tags);
			m1.setTags(tags);
		} else if (Query.EL.sn == el) {
			Metadata m2 = query.getMeta();
			int sn = Integer.parseInt(value);
			if (sn < 1) {
                throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Start number has to be greater than 0.");
            }
			m2.setStart(sn);
		} else if (Query.EL.ln == el) {
			Metadata m3 = query.getMeta();
			int len = Integer.parseInt(value);
			if (len < 1)
                throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Length has to be greater than 0.");
			m3.setRows(len);
		} else if (Query.EL.so == el) {
			Metadata m4 = query.getMeta();
			m4.setSearchOptions(value);
		} else if (Query.EL.ud == el) {
			Metadata m5 = query.getMeta();
			String[] list = value.split(COMMA_SEPARATOR);
			HashMap<String, String> map = new HashMap<String, String>();
			for (int k = 0; k < list.length; k++) {
				String[] keyValue = list[k].split(COLON_SEPARATOR);
				// key:value 매핑.
				if (keyValue.length == 2) {
					removeEscape(keyValue);
					map.put(keyValue[0].toUpperCase(), keyValue[1]);
				}
			}
			m5.setUserData(map);
		} else if (Query.EL.fl == el) {
			String[] list = value.split(COMMA_SEPARATOR);
			ViewContainer views = new ViewContainer(list.length);
			for (int k = 0; k < list.length; k++) {
				String[] str = list[k].split(COLON_SEPARATOR);
				if (str.length > 2) {
					views.add(new View(str[0].trim(), Integer.parseInt(str[1].trim()), Integer.parseInt(str[2].trim())));
				} else if (str.length > 1) {
					views.add(new View(str[0].trim(), Integer.parseInt(str[1].trim())));
				} else {
					views.add(new View(list[k]));
				}
			}
			query.setViews(views);
		} else if (Query.EL.se == el) {

			if (value.length() > 0) {
				Object obj = makeClause(value);
				Clause clause = null;
				if(obj instanceof Term){
					clause = new Clause((Term) obj);
				}else{
					clause = (Clause) makeClause(value);
				}
				query.setClause(clause);
			}
		} else if (Query.EL.ft == el) {
			Filters f = makeFilters(value);
			query.setFilters(f);
		} else if (Query.EL.gr == el) {
			String[] list = value.split(COMMA_SEPARATOR);
			Groups g = new Groups();
			for (int k = 0; k < list.length; k++) {
				String[] items = list[k].split(COLON_SEPARATOR);
				if (items.length < 2) {
					// 정보부족 에러.
                    throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Grouping syntax is invalid : " + list[k]);
				}

				String field = items[0].trim();
				// String shortFunctionName = items[1];
				int limit = -1;
				int sortOrder = 0;

				// function은 ; 구분으로 파라미터 나눈다.
				// 기능이름;파라미터1;파라미터2 등둥..

				String[] functionList = items[1].split(SEMICOLON_SEPARATOR);
				if (items.length > 2) {
					if (items.length > 3) {
						// 마지막은 limit이다.
						limit = Integer.parseInt(items[3].trim());
					}
					// items[2]는 정렬옵션
					sortOrder = getGroupSortOrder(items[2].trim());
				}

				GroupFunction[] groupFunctions = new GroupFunction[functionList.length];
				for (int j = 0; j < functionList.length; j++) {
					String functionExpr = functionList[j];
					String functionName = null;
					String param = null;
					if (functionExpr.contains("(")) {

						String[] funcTmp = functionExpr.split("\\(");
						functionName = funcTmp[0];
						param = funcTmp[1].substring(0, funcTmp[1].length() - 1);

					} else {
						functionName = functionExpr;
					}

                    if(functionName != null) {
                        functionName = functionName.toUpperCase();
                    }
                    try{
                        GroupFunctionType groupFunctionType = GroupFunctionType.valueOf(functionName);
                        groupFunctions[j] = new GroupFunction(groupFunctionType, sortOrder, param);
                    } catch (Exception e) {
                        throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Unknown group function '"+ functionName +"'.");
					}

                }

                g.add(new Group(field, groupFunctions, sortOrder, limit));

			}
			query.setGroups(g);
		} else if (Query.EL.gf == el) {
			Filters f = makeFilters(value);
			query.setGroupFilters(f);
		} else if (Query.EL.ra == el) {
			Sorts s = new Sorts();
			String[] list = value.split(COMMA_SEPARATOR);
			for (int k = 0; k < list.length; k++) {
				String[] str = list[k].split(COLON_SEPARATOR);
				if (str.length > 1) {

					boolean isAsc, isShuffle;

					// asc일 시 isAsc true
					if (str[1].equalsIgnoreCase("asc") || str[1].equalsIgnoreCase("asc_shuffle")) {
						isAsc = true;
					} else {
						isAsc = false;
					}

					//_shuffle 로 endwith 면, isShuffle 을 true
					if (str[1].endsWith("_shuffle") || str[1].endsWith("_SHUFFLE")) {
						isShuffle = true;
					} else {
						isShuffle = false;
					}

					s.add(new Sort(str[0], isAsc, isShuffle));
				} else {
					s.add(new Sort(list[k]));
				}

			}
			query.setSorts(s);
		} else if (Query.EL.qm == el) {
			Metadata m = query.getMeta();
			QueryModifier queryModifier = (QueryModifier) DynamicClassLoader.loadObject(value);
            if(queryModifier == null) {
                throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Cannot find query modifier '"+ value +"'.");
            }
			m.setQueryModifier(queryModifier);
		} else if (Query.EL.rm == el) {
			Metadata m = query.getMeta();
			ResultModifier resultModifier = (ResultModifier) DynamicClassLoader.loadObject(value);
            if(resultModifier == null) {
                throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Cannot find result modifier '"+ value +"'.");
            }
			m.setResultModifier(resultModifier);
		} else if (Query.EL.sp == el) {
			Metadata m = query.getMeta();
			StoredProcedure sp = (StoredProcedure) DynamicClassLoader.loadObject(value);
            if(sp == null) {
                throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Cannot find stored procedure '"+ value +"'.");
            }
			m.setStoredProcedure(sp);
		} else if (Query.EL.bd == el) {
			String[] list = value.split(SEMICOLON_SEPARATOR);
			String[] list1 = list[0].split(COLON_SEPARATOR);
			
			Bundle b = new Bundle(list1[0].trim());
			if(list1.length > 1) {
				int bundleRows = 5; //default 5개.
				try{
					bundleRows = Integer.parseInt(list1[1].trim());
				}catch(NumberFormatException e) {
					//ignore
				}
				b.setRows(bundleRows);
			}

            if(list1.length > 2) {
                int option = Bundle.OPT_PARENT_INCLUDE; //대표포함.
                try{
                    option = Integer.parseInt(list1[2].trim());
                }catch(NumberFormatException e) {
                    //ignore
                }
                b.setOption(option);
            } else {
                b.setOption(Bundle.OPT_PARENT_INCLUDE);
            }

			if(list.length > 1) {
				Sorts s = new Sorts();
				String[] sortList = list[1].split(COMMA_SEPARATOR);
				for (int k = 0; k < sortList.length; k++) {
					String[] str = sortList[k].split(COLON_SEPARATOR);
					if (str.length > 1) {
						boolean isAsc = str[1].equalsIgnoreCase("asc");
						s.add(new Sort(str[0], isAsc));
					} else {
						s.add(new Sort(sortList[k]));
					}

                }
				b.setSorts(s);
			}
			query.setBundle(b);
		}
	}

	/**
	 * @Deprecated 대신 parseQuery(QueryMap queryMap) 를 사용하기 바람.
	 * */
	public Query parseQuery(String queryString) {
        String[] groups = queryString.split(SECTION_SEPARATOR);
        Query query = new Query();
        query.setMeta(new Metadata());
        for (int i = 0; i < groups.length; i++) {
            String[] tmp = groups[i].split(VALUE_SEPARATOR);
            if (tmp.length < 2) {
                continue;
            }
            String key = tmp[0];
            String value = tmp[1];

            if (value == null || value.length() == 0) {
                continue;
            }
            fillQuery(query, key, value);
        }

        logger.debug("query = {}", query);
        return query;
	}

	private static String convertToClassName(String groupFunctionName) {
		String tempClassName = null;
		String packageName = null;
		String className = "";

		if (groupFunctionName.contains(".")) {
			int idx = groupFunctionName.lastIndexOf(".");
			packageName = groupFunctionName.substring(0, idx);
			tempClassName = groupFunctionName.substring(idx + 1);
		} else {
			tempClassName = groupFunctionName;
		}

		if (tempClassName.contains("_")) {
			String[] parts2 = tempClassName.split("_");
			for (int i = 0; i < parts2.length; i++) {
				className += capitalize(parts2[i]);
			}
		} else {
			className = capitalize(groupFunctionName);
		}

		if (packageName != null) {
			return PLUGIN_PACKAGE_PATH + "." + packageName + "." + className + PLUGIN_CLASSNAME_SUFFIX;
		} else {
			return PLUGIN_PACKAGE_PATH + "." + className + PLUGIN_CLASSNAME_SUFFIX;
		}

	}

	private static String capitalize(String str) {
		char firstChar = str.charAt(0);

		if (firstChar >= 'a' && firstChar <= 'z') {
			return ((char) (firstChar - 32)) + str.substring(1);
		} else {
			return str;
		}

	}

	private int getGroupSortOrder(String string) {
		if (string.equalsIgnoreCase("KEY_ASC")) {
			return Group.SORT_KEY_ASC;
		} else if (string.equalsIgnoreCase("KEY_DESC")) {
			return Group.SORT_KEY_DESC;
		} else if (string.equalsIgnoreCase("COUNT_ASC")) {
			return Group.SORT_VALUE_ASC;
		} else if (string.equalsIgnoreCase("COUNT_DESC")) {
			return Group.SORT_VALUE_DESC;
		}
		return 0;
	}

	//
	// TODO 괄호없이 A or B and C 형태도 가능토록, escapse 문자 '\' 적용필요.
	//
	protected Object makeClause(String value) {
		logger.trace("makeClause = {}", value);
        if (value.charAt(0) == '{') {
            int pos = findMatchBrace(value, 1);

            if (pos < 0) {
                throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Close brace not found : " + value);
            }
            // logger.debug("pos={}, value={}",pos, value);
            Object operand1 = makeClause(value.substring(1, pos));
            if (value.regionMatches(true, pos + 1, Clause.Operator.OR.name(), 0, 2)) {
                int end = findMatchBrace(value, pos + 4);// value.indexOf('}', pos + 4);
                Object operand2 = makeClause(value.substring(pos + 4, end));
                if(operand2 instanceof Clause) {
                    Clause innerClause = (Clause)operand2;
                    if(innerClause.operator() == Clause.Operator.NOT && innerClause.operand1() == null) {
                        logger.info("OR-NOT Clause detected : [{}]OR[NOT[{}]]", operand1, innerClause.operand2());
                    }
                }

                return new Clause(operand1, Clause.Operator.OR, operand2);
            } else if (value.regionMatches(true, pos + 1, Clause.Operator.AND.name(), 0, 3)) {
                int end = findMatchBrace(value, pos + 5);// value.indexOf('}', pos + 5);
                Object operand2 = makeClause(value.substring(pos + 5, end));
                if(operand2 instanceof Clause) {
                    Clause innerClause = (Clause)operand2;
                    if(innerClause.operator() == Clause.Operator.NOT && innerClause.operand1() == null) {
                        return new Clause(operand1, Clause.Operator.NOT, innerClause.operand2());
                    }
                }
                return new Clause(operand1, Clause.Operator.AND, operand2);
            } else if (value.regionMatches(true, pos + 1, Clause.Operator.NOT.name(), 0, 3)) {
                int end = findMatchBrace(value, pos + 5);// value.indexOf('}', pos + 5);
                Object operand2 = makeClause(value.substring(pos + 5, end));
                return new Clause(operand1, Clause.Operator.NOT, operand2);
            } else if (value.regionMatches(true, pos + 1, Clause.Operator.BOOST.name(), 0, 5)) {
                int end = findMatchBrace(value, pos + 7);
                Object operand2 = makeClause(value.substring(pos + 7, end));
                if(operand2 instanceof Clause) {
                    Clause innerClause = (Clause)operand2;
                    if(innerClause.operator() == Clause.Operator.NOT && innerClause.operand1() == null) {
                        throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "BOOST-uni NOT is not supported : " + value);
                    }
                }
                return new Clause(operand1, Clause.Operator.BOOST, operand2);
            } else if (value.regionMatches(true, pos + 1, Clause.Operator.LOR.name(), 0, 3)) {
                int end = findMatchBrace(value, pos + 5);
                Object operand2 = makeClause(value.substring(pos + 5, end));
                if(operand2 instanceof Clause) {
                    Clause innerClause = (Clause)operand2;
                    if(innerClause.operator() == Clause.Operator.NOT && innerClause.operand1() == null) {
                        logger.info("OR-NOT Clause detected : [{}]OR[NOT[{}]]", operand1, innerClause.operand2());
                    }
                }

                return new Clause(operand1, Clause.Operator.LOR, operand2);
            } else {
                // operator가 없거나 잘못되었으면 뒤는 무시하고 operand1 이 term으로 간주된다.
                if (operand1 instanceof Term)
                    return new Clause(operand1);
                else
                    return operand1;
            }

        } else {
            /*
             * Unary NOT을 지원하기위함. 예) NOT{title,body:AND(방송):100:32}
             */
            if (value.startsWith(Clause.Operator.NOT.name()+"{")) {
                int end = findMatchBrace(value, 4);
                Object operand2 = makeClause(value.substring(4, end));
                return new Clause(null, Clause.Operator.NOT, operand2);
            } else {
                Term term = makeTerm(value);
                return term;
            }
        }


	}

	private int findMatchBrace(String value, int m) {
		int depth = 0;
		for (; m < value.length(); m++) {
			// logger.debug(m+"="+value.charAt(m));
			if (value.charAt(m) == '{' && ( m == 0 || value.charAt(m - 1) != '\\')) {
				depth++;
				// logger.debug("depth="+depth);
			} else if (value.charAt(m) == '}' && ( m == 0 || value.charAt(m - 1) != '\\')) {
				// logger.debug("depth="+depth);
				if (depth == 0)
					return m;

				depth--;
				// logger.debug("depth="+depth);
			}
		}
		return -1;
	}

	private Term makeTerm(String value) {
        logger.debug("Term => {}", value);
        String[] list = value.split(COLON_SEPARATOR);
        int[] proximity = new int[1];
        if (list.length == 1) {
            throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Search field must contain field id and keyword : " + value);

        } else if (list.length == 2) {
            // field:term
            String[] fieldList = list[0].replaceAll(" ", "").split(COMMA_SEPARATOR);
            String[] term = new String[1];
            Term.Type type = getType(list[1], term, proximity);
            return new Term(fieldList, removeEscape(term[0]), type).withProximity(proximity[0]);
        } else if (list.length == 3) {
            // field:term:score
            String[] fieldList = list[0].replaceAll(" ", "").split(COMMA_SEPARATOR);
            String[] term = new String[1];
            Term.Type type = getType(list[1], term, proximity);
            return new Term(fieldList, removeEscape(term[0]), Integer.parseInt(list[2]), type).withProximity(proximity[0]);
        } else if (list.length == 4) {
            // field:term:score:option
            String[] fieldList = list[0].replaceAll(" ", "").split(COMMA_SEPARATOR);
            String[] term = new String[1];
            Term.Type type = getType(list[1], term, proximity);
            return new Term(fieldList, removeEscape(term[0]), Integer.parseInt(list[2]), type, new Option(Integer.parseInt(list[3]))).withProximity(proximity[0]);
        } else {
            throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Search field has too many options. Check search keyword escape colon symbol. : " + value);
        }

	}

	private Term.Type getType(String str, String[] term, int[] proximity) {
		if (str.startsWith("ALL(")) {
            if(str.endsWith(")")) {
                term[0] = str.substring(4, str.length() - 1);
            } else {
                int p = str.lastIndexOf(")");
                if (str.length() > p + 2) {
                    char ch = str.charAt(p + 1);
                    if (ch == '~') {
                        String proximityStr = str.substring(p + 2);
                        proximityStr = proximityStr.trim();
                        if(proximity.length == 0){
                            throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Proximity cannot be empty.");
                        }
                        try {
                            proximity[0] = Integer.parseInt(proximityStr);
                        } catch (NumberFormatException e) {
                            throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Proximity is not an integer number : " + proximityStr);
                        }
                    }
                    term[0] = str.substring(4, p);
                }
            }
            return Term.Type.ALL;
        } else if (str.startsWith("ANY(")) {
//			term[0] = str.substring(4, str.length() - 1);
            if(str.endsWith(")")) {
                term[0] = str.substring(4, str.length() - 1);
            } else {
                int p = str.lastIndexOf(")");
                if (str.length() > p + 2) {
                    char ch = str.charAt(p + 1);
                    if (ch == '~') {
                        String proximityStr = str.substring(p + 2);
                        proximityStr = proximityStr.trim();
                        if(proximity.length == 0){
                            throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Proximity cannot be empty.");
                        }
                        try {
                            proximity[0] = Integer.parseInt(proximityStr);
                        } catch (NumberFormatException e) {
                            throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Proximity is not an integer number : " + proximityStr);
                        }
                    }
                    term[0] = str.substring(4, p);
                }
            }
			return Term.Type.ANY;
		} else if (str.startsWith("EXT(")) {
			term[0] = str.substring(4, str.length() - 1);
			logger.debug("str = " + str);
			return Term.Type.EXT;
		} else if (str.startsWith("PHRASE(")) {
			term[0] = str.substring(7, str.length() - 1);
			logger.debug("str = " + str);
			return Term.Type.PHRASE;
		} else if (str.startsWith("BOOL(")) {
			term[0] = str.substring(5, str.length() - 1);
			logger.debug("str = " + str);
			return Term.Type.BOOL;
		}
		// 통째로 반환. default = AND
		term[0] = str;
		return Term.Type.ALL;
	}

	private Filters makeFilters(String value) {
		//
		// Syntax : [FieldId;..] : [Filter Function] [(param;..)] : [Value;..] : [Boosting Score]
		//
		String[] list = value.split(COMMA_SEPARATOR);
		Filters f = new Filters();
		for (int k = 0; k < list.length; k++) {
			String[] str = list[k].split(COLON_SEPARATOR);
			if (str.length <= 1) {
				throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Filter syntax is invalid : " + value);
			}
//				else if (str.length <= 2) {
//				logger.error("Filter param string is empty. >> {}", value);
//				return f;
//			}
			String field = str[0];
			String function = str[1];
			String[] fieldList = field.split(SEMICOLON_SEPARATOR);
			//method 함수에 딸린 파라미터. 괄호안에 ; 구분으로 들어있음.
            String[] functionParamList = null;
            if(function.contains("(") && function.contains(")")) {
                String tmp = function;
                int spos = tmp.indexOf("(");
                int epos = tmp.lastIndexOf(")");
                function = tmp.substring(0, spos);
                String paramStr = tmp.substring(spos + 1, epos);
				functionParamList = paramStr.split(SEMICOLON_SEPARATOR);
                for(int i = 0; i < functionParamList.length; i++) {
					functionParamList[i] = functionParamList[i].trim();
                }
            }
			String[] parameterList = null;
			if(str.length > 2){
				parameterList = str[2].split(SEMICOLON_SEPARATOR);
				removeEscape(parameterList);
			}
			int boostScore = 0;
			if(str.length > 3){
				boostScore = Integer.parseInt(str[3]);
			}
			if (function.equalsIgnoreCase("MATCH")) {
				f.add(new Filter(field, Filter.MATCH, functionParamList, parameterList));
			} else if (function.equalsIgnoreCase("SECTION")) {
				String[] patList = new String[parameterList.length];
				String[] endPatList = new String[parameterList.length];

				for (int i = 0; i < parameterList.length; i++) {
					String[] range = parameterList[i].split(RANGE_SEPARATOR);
					patList[i] = range[0];
					if (range.length >= 2) {
						endPatList[i] = range[1];
						logger.debug("[" + patList[i] + "~" + endPatList[i] + "]");
					} else {
						endPatList[i] = "";
						logger.debug("[" + patList[i] + "~]");
					}
				}
				f.add(new Filter(field, Filter.SECTION, functionParamList, patList, endPatList));
			} else if (function.equalsIgnoreCase("PREFIX")) {
				f.add(new Filter(field, Filter.PREFIX, functionParamList, parameterList));
			} else if (function.equalsIgnoreCase("SUFFIX")) {
				f.add(new Filter(field, Filter.SUFFIX, functionParamList, parameterList));
			} else if (function.equalsIgnoreCase("MATCH_BOOST")) {
				f.add(new Filter(field, Filter.MATCH_BOOST, functionParamList, parameterList, boostScore));
			} else if (function.equalsIgnoreCase("SECTION_BOOST")) {
				if (str.length >= 4) {
					String[] patList = new String[parameterList.length];
					String[] endPatList = new String[parameterList.length];
					for (int i = 0; i < parameterList.length; i++) {
						String[] range = parameterList[i].split(RANGE_SEPARATOR);
						patList[i] = range[0];
						if (range.length >= 2)
							endPatList[i] = range[1];
						else
							endPatList[i] = "";
					}
					f.add(new Filter(field, Filter.SECTION_BOOST, functionParamList, patList, endPatList, boostScore));
				} else {
					logger.warn("SECTION_BOOST Pattern string or boost score is empty.");
				}
			} else if (function.equalsIgnoreCase("PREFIX_BOOST")) {
				f.add(new Filter(field, Filter.PREFIX_BOOST, functionParamList, parameterList, boostScore));
			} else if (function.equalsIgnoreCase("SUFFIX_BOOST")) {
				f.add(new Filter(field, Filter.SUFFIX_BOOST, functionParamList, parameterList, boostScore));
			} else if (function.equalsIgnoreCase("EXCLUDE")) {
				f.add(new Filter(field, Filter.EXCLUDE, functionParamList, parameterList));
			} else if (function.equalsIgnoreCase("EXCLUDE_BOOST")) {
				f.add(new Filter(field, Filter.EXCLUDE_BOOST, functionParamList, parameterList, boostScore));
			} else if (function.equalsIgnoreCase("BOOST")) {
				f.add(new Filter(field, Filter.BOOST));
            } else if (function.equalsIgnoreCase("GEO_RADIUS")) {
				//다중 필드지원.
                Filter filter = new Filter(fieldList, Filter.GEO_RADIUS, functionParamList, parameterList);
                f.add(filter);
            } else if (function.equalsIgnoreCase("GEO_RADIUS_BOOST")) {
				//다중 필드지원.
                Filter filter = new Filter(fieldList, Filter.GEO_RADIUS_BOOST, functionParamList, parameterList, boostScore);
                f.add(filter);
			} else {
                throw new SearchError(ServerErrorCode.QUERY_SYNTAX_ERROR, "Filter method '" + function + "' is unknown.");

			}

		}
		return f;

	}
}
