/*
 *    Copyright 2009-2021 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * @author Clinton Begin
 * <resultMap id = ” rrn4testProTool ” type = ” User ” >
 * <id column = ”id” property = ”id” />
 * <result property = ”orders[0].items[0].name” column = ”item1” />
 * <result property =”orders[O].items [1].name” column = ”item2” />
 * </resultMap>
 *
 * 在上例 中 ，“ orders[O] . items[O] . name ”这种 由 “ ．”和“ ［］ ”组成的表达式是由 PropertyTokenizer
 * 进行解析的。
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
  //当前表达式的名称,如：orders
  private String name;
  //当前表达式的索引名,如：orders[O]
  private final String indexedName;
  //索 引 下标,如：O
  private String index;
  //子表达式,,如：items[0].name
  private final String children;

  public PropertyTokenizer(String fullname) {
    //查找 ” · ” 的位置
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      name = fullname.substring(0, delim);//初始化name
      children = fullname.substring(delim + 1);//初始化children
    } else {
      name = fullname;
      children = null;
    }
    indexedName = name;//初始化indexedName
    delim = name.indexOf('[');
    if (delim > -1) {
      index = name.substring(delim + 1, name.length() - 1);//初始化index
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
