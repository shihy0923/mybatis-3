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
package org.apache.ibatis.parsing;

/**
 * @author Clinton Begin
 * GenericTokenParser 是一个通用的字占位符解析器
 */
public class GenericTokenParser {

  //占位符的开始标记
  private final String openToken;
  //占位符的结束标记
  private final String closeToken;
  //TokenHandler接口的实现会按照一定的逻辑解析占位符
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  public String parse(String text) {
    //检测text是否为空
    if (text == null || text.isEmpty()) {
      return "";
    }
    // search open token
    //查找开始标记
    int start = text.indexOf(openToken);
    if (start == -1) {//没找到开始标记，直接返回
      return text;
    }
    char[] src = text.toCharArray();
    int offset = 0;
    //用来记录解析后的字符串
    final StringBuilder builder = new StringBuilder();
    StringBuilder expression = null;
    do {
      if (start > 0 && src[start - 1] == '\\') {
        // this open token is escaped. remove the backslash and continue.
        //遇到转义的开始标记，则直接将前面的字符串以及开始标记追加到 builder 中
        //因为 openToken 前面一个位置是 \ 转义字符，所以忽略 \
        //添加 [offset, start - offset - 1] 和 openToken 的内容，添加到 builder 中
        builder.append(src, offset, start - offset - 1).append(openToken);
        offset = start + openToken.length();
      } else {
        // found open token. let's search close token.
        //查找到开始标记，且未转义
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }
        //将openToken前面的字符串追加到builder中
        builder.append(src, offset, start - offset);
        // 修改 offset,移动到openToken后面的一个位置
        offset = start + openToken.length();
        //从openToken后面的一个位置开始找结束占位符
        int end = text.indexOf(closeToken, offset);
        while (end > -1) {
          // 转义
          if (end > offset && src[end - 1] == '\\') {
            // this close token is escaped. remove the backslash and continue.
            // 因为 endToken 前面一个位置是 \ 转义字符，所以忽略 \
            // 添加 [offset, end - offset - 1] 和 endToken 的内容，添加到 builder 中
            expression.append(src, offset, end - offset - 1).append(closeToken);
            //将offset移动到closeToken后面一个位置
            offset = end + closeToken.length();
            //从closeToken后面一个位置重新开始寻找结束占位符
            end = text.indexOf(closeToken, offset);
          } else {// 非转义
            //将开始标记和结束标记之间的字符串追加到 expression 中保存
            expression.append(src, offset, end - offset);
            break;
          }
        }
        if (end == -1) {//未找到结束标记
          // close token was not found.
          //closeToken 未找到，直接拼接openToken后面的内容
          builder.append(src, start, src.length - start);
          //讲offset设置到最后的位置
          offset = src.length;
        } else {//找到了结束标记
          //一般走这里
          //结束标记找到，将 expression 提交给 handler 处理 ，并将处理结果添加到 builder 中
          builder.append(handler.handleToken(expression.toString()));
          //将offset设置到closeToken后面一个位置
          offset = end + closeToken.length();
        }
      }
      //继续，从新的offset位置，寻找开始标记openToken的位置
      start = text.indexOf(openToken, offset);
    } while (start > -1);
    if (offset < src.length) {//拼接剩余的部分
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
