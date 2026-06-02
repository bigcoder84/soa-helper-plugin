package cn.bigcoder.soa.helper.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 模板表达式解析器
 *
 * 简化语法：
 * 1. 变量：${appId}、${methodName} - 变量必须用 ${} 包裹
 * 2. 函数：${lower(${methodName})}、${urlEncode(${appId})}
 * 3. 嵌套函数：${urlEncode(${lower(${methodName})})}
 * 4. 复杂参数：${base64Encode({"appId": "${appId}", "method":"${methodName}"})}
 * 5. 引号包裹：${lower("text(with)parens")} - 用于消除括号等歧义字符的二义性
 * 6. 混合使用：https://example.com?app=${appId}&method=${lower(${methodName})}
 *
 * 关键特性：
 * - 所有变量必须用 ${} 包裹，避免歧义
 * - 变量和函数统一，变量可看作无参函数
 * - 函数参数可以包含任意字符，支持嵌套的 $ 引用
 * - 支持引号包裹参数以明确边界（当参数包含括号等歧义字符时）
 * - 清晰、无歧义的语法规则
 *
 * 语法使用场景：
 * - 简单变量：${appId}、${methodName}
 * - 变量边界：${methodName}_suffix - ${} 明确变量边界
 * - 简单函数：${lower(${methodName})}
 * - 复杂参数：${lower("text(with)parens")} - 用引号明确括号边界
 * - 引号内变量：${lower("prefix_${methodName}_suffix")} - 结合使用引号和 ${}
 * - 引号转义：${lower("text with \"quotes\"")}
 *
 * 支持的内置变量：
 * - appId: 应用ID
 * - methodName: 方法名
 *
 * 支持的内置函数：
 * - lower(str): 转小写
 * - upper(str): 转大写
 * - urlEncode(str): URL编码
 * - urlDecode(str): URL解码
 * - base64Encode(str): Base64编码
 * - base64Decode(str): Base64解码
 */
public class TemplateParser {
    
    // 内置函数映射
    private final Map<String, Function<String, String>> functions = new HashMap<>();
    
    // 内置变量（在解析时动态设置）
    private final Map<String, String> variables = new HashMap<>();
    
    public TemplateParser() {
        registerBuiltInFunctions();
    }
    
    /**
     * 注册内置函数
     */
    private void registerBuiltInFunctions() {
        // 转小写
        functions.put("lower", String::toLowerCase);
        
        // 转大写
        functions.put("upper", String::toUpperCase);
        
        // URL编码
        functions.put("urlEncode", str -> {
            try {
                return URLEncoder.encode(str, StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                return str;
            }
        });
        
        // URL解码
        functions.put("urlDecode", str -> {
            try {
                return URLDecoder.decode(str, StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                return str;
            }
        });
        
        // Base64编码
        functions.put("base64Encode", str -> 
            Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8))
        );
        
        // Base64解码
        functions.put("base64Decode", str -> {
            try {
                return new String(Base64.getDecoder().decode(str), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return str;
            }
        });
    }
    
    /**
     * 解析模板
     * 
     * @param template 模板字符串
     * @param vars 变量映射
     * @return 解析后的字符串
     */
    public String parse(String template, Map<String, String> vars) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        // 保存变量到实例变量
        this.variables.clear();
        this.variables.putAll(vars);
        
        // 从左到右扫描并替换所有 $ 引用
        return parseExpression(template);
    }
    
    /**
     * 解析表达式，处理其中的所有 $xxx 或 $xxx(...) 引用
     * 
     * @param text 待解析的文本
     * @return 解析后的文本
     */
    private String parseExpression(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        StringBuilder result = new StringBuilder();
        int i = 0;
        
        while (i < text.length()) {
            char c = text.charAt(i);
            
            if (c == '$') {
                // 找到 $ 符号，开始解析变量或函数
                int[] endPos = new int[1];
                String value = parseReference(text, i, endPos);
                result.append(value);
                i = endPos[0];
            } else {
                // 普通字符，直接添加
                result.append(c);
                i++;
            }
        }
        
        return result.toString();
    }
    
    /**
     * 从指定位置解析一个 $ 引用（变量或函数调用）
     * 
     * 只支持 ${...} 语法，必须用大括号包裹
     * 
     * @param text 文本
     * @param startPos $ 符号的位置
     * @param endPos 输出参数，返回解析结束的位置
     * @return 解析后的值
     */
    private String parseReference(String text, int startPos, int[] endPos) {
        int i = startPos + 1; // 跳过 $
        
        // 必须是 ${...} 语法
        if (i >= text.length() || text.charAt(i) != '{') {
            // 如果没有 {，返回原始的 $ 符号
            endPos[0] = startPos + 1;
            return "$";
        }
        
        i++; // 跳过 {
        
        // 提取函数/变量名
        StringBuilder name = new StringBuilder();
        while (i < text.length() && isIdentifierChar(text.charAt(i))) {
            name.append(text.charAt(i));
            i++;
        }
        
        String funcName = name.toString();
        
        // 检查是否是函数调用（后面跟着括号）
        if (i < text.length() && text.charAt(i) == '(') {
            // 函数调用，提取参数
            int[] argEndPos = new int[1];
            String argument = extractArgument(text, i, argEndPos);
            i = argEndPos[0];
            
            // 必须有结束的 }
            if (i < text.length() && text.charAt(i) == '}') {
                i++; // 跳过 }
            }
            
            // 递归解析参数中的 $ 引用
            String parsedArg = parseExpression(argument);
            
            // 应用函数
            String result = applyFunction(funcName, parsedArg);
            endPos[0] = i;
            return result;
        } else {
            // 变量引用（无参函数）
            // 必须有结束的 }
            if (i < text.length() && text.charAt(i) == '}') {
                i++; // 跳过 }
            }
            
            endPos[0] = i;
            return applyFunction(funcName, "");
        }
    }
    
    /**
     * 提取函数参数（括号内的内容）
     * 支持引号包裹参数以明确边界，消除二义性
     * 
     * 语法规则：
     * 1. 简单参数（无歧义字符）：$lower($methodName)
     * 2. 复杂参数（包含括号等）：$lower("text(with)parens")
     * 3. 引号内支持变量：$lower("prefix_$methodName_suffix")
     * 4. 引号转义：$lower("text with \"quotes\"")
     * 
     * @param text 文本
     * @param startPos 左括号的位置
     * @param endPos 输出参数，返回右括号之后的位置
     * @return 参数内容（不包含括号和外层引号）
     */
    private String extractArgument(String text, int startPos, int[] endPos) {
        int i = startPos + 1; // 跳过左括号
        StringBuilder arg = new StringBuilder();
        
        // 跳过空白字符
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        
        // 检查是否以引号开始
        if (i < text.length() && (text.charAt(i) == '"' || text.charAt(i) == '\'')) {
            char quoteChar = text.charAt(i);
            i++; // 跳过开始引号
            
            // 提取引号内的内容
            boolean escaped = false;
            while (i < text.length()) {
                char c = text.charAt(i);
                
                if (escaped) {
                    // 处理转义字符
                    switch (c) {
                        case 'n': arg.append('\n'); break;
                        case 'r': arg.append('\r'); break;
                        case 't': arg.append('\t'); break;
                        case '\\': arg.append('\\'); break;
                        case '"': arg.append('"'); break;
                        case '\'': arg.append('\''); break;
                        default: 
                            arg.append('\\');
                            arg.append(c);
                    }
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quoteChar) {
                    // 找到结束引号
                    i++; // 跳过结束引号
                    break;
                } else {
                    arg.append(c);
                }
                i++;
            }
            
            // 跳过引号后的空白字符
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            
            // 必须是右括号
            if (i < text.length() && text.charAt(i) == ')') {
                i++; // 跳过右括号
            }
            
            endPos[0] = i;
            return arg.toString();
        } else {
            // 没有引号，使用括号深度匹配（原有逻辑）
            int depth = 1;

            while (i < text.length() && depth > 0) {
                char c = text.charAt(i);

                if (c == '\'' || c == '"') {
                    char stringQuote = c;
                    arg.append(c);
                    i++;
                    while (i < text.length()) {
                        char sc = text.charAt(i);
                        arg.append(sc);
                        i++;
                        if (sc == '\\') {
                            if (i < text.length()) {
                                arg.append(text.charAt(i));
                                i++;
                            }
                        } else if (sc == stringQuote) {
                            break;
                        }
                    }
                } else if (c == '(') {
                    depth++;
                    arg.append(c);
                    i++;
                } else if (c == ')') {
                    depth--;
                    if (depth > 0) {
                        arg.append(c);
                    }
                    i++;
                } else {
                    arg.append(c);
                    i++;
                }
            }

            endPos[0] = i;
            return arg.toString();
        }
    }
    
    /**
     * 判断字符是否是标识符字符
     */
    private boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
    
    /**
     * 应用函数或获取变量值
     * 
     * @param name 函数/变量名
     * @param argument 参数（对于变量为空字符串）
     * @return 结果值
     */
    private String applyFunction(String name, String argument) {
        // 首先检查是否是变量
        if (argument.isEmpty() && variables.containsKey(name)) {
            return variables.get(name);
        }
        
        // 然后检查是否是函数
        Function<String, String> function = functions.get(name);
        if (function != null) {
            return function.apply(argument);
        }
        
        // 未知的函数/变量，返回原始文本（保持 ${} 格式）
        if (argument.isEmpty()) {
            return "${" + name + "}";
        } else {
            return "${" + name + "(" + argument + ")}";
        }
    }

    /**
     * 获取所有支持的函数名称
     *
     * @return 函数名称列表
     */
    public String[] getSupportedFunctions() {
        return functions.keySet().toArray(new String[0]);
    }
}
