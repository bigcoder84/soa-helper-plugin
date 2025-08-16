package cn.bigcoder.soa.helper.util;

/**
 * @author: Jindong.Tian
 * @date: 2025-07-26
 **/
public class KeywordUtil {


    /**
     * 搜索词匹配候选词并高亮候选词
     *
     * @param input 候选词
     * @param query 搜索词
     * @param openTag 高亮词left标签
     * @param closeTag 高亮词right标签
     * @return 返回高亮后的字符串
     */
    public static String highlightMatches(String input, String query, String openTag, String closeTag) {
        if (query.isEmpty() || input.isEmpty()) {
            return input;
        }

        // 将查询和输入都转为小写用于匹配
        String lowerQuery = query.toLowerCase();
        String lowerInput = input.toLowerCase();

        // 标记所有需要高亮的字符位置
        boolean[] matched = new boolean[input.length()];

        int queryIndex = 0; // 当前需要匹配的查询字符索引
        int inputIndex = 0; // 当前正在检查的输入字符索引

        // 遍历输入字符串，标记所有按顺序匹配的字符
        while (queryIndex < lowerQuery.length() && inputIndex < lowerInput.length()) {
            if (lowerInput.charAt(inputIndex) == lowerQuery.charAt(queryIndex)) {
                // 字符匹配，标记并移动到下一个查询字符
                matched[inputIndex] = true;
                queryIndex++;
            }
            // 无论是否匹配，都移动到下一个输入字符
            inputIndex++;
        }

        // 构建结果字符串，添加高亮标签
        StringBuilder result = new StringBuilder();
        boolean inHighlight = false;

        for (int i = 0; i < input.length(); i++) {
            if (matched[i] && !inHighlight) {
                // 开始高亮
                result.append(openTag);
                inHighlight = true;
            } else if (!matched[i] && inHighlight) {
                // 结束高亮
                result.append(closeTag);
                inHighlight = false;
            }
            result.append(input.charAt(i));
        }

        // 关闭可能未关闭的高亮标签
        if (inHighlight) {
            result.append(closeTag);
        }

        return result.toString();
    }
}
