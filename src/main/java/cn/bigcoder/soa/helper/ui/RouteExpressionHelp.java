package cn.bigcoder.soa.helper.ui;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 路由条件表达式语法说明，设置页帮助与路由编辑对话框共用
 */
final class RouteExpressionHelp {

    /**
     * 示例表达式（单测保证每条都能通过校验）
     */
    static final List<String> EXAMPLES = List.of(
            "${appId} in (\"100017451\", \"100017452\")",
            "${appId} startsWith \"1000174\" && ${methodName} not in (\"ping\")",
            "${appId} >= 100017000 and ${appId} < 100018000",
            "lower(${methodName}) matches \"get.*\" || substring(${appId}, 0, 4) == \"1000\"");

    /**
     * 语法说明 HTML 片段（不含 html/body 标签）
     */
    static final String SECTION =
            "按顺序匹配，第一条条件成立的路由生效；都不成立时使用兜底 URL 模板。"
                    + "条件计算出错的路由会被跳过并给出提示。<br/><br/>" +
            "<b>变量：</b><code>${appId}</code> 应用ID、<code>${methodName}</code> 方法名；"
                    + "也可省略 <code>${}</code>，直接写 <code>appId</code><br/>" +
            "<b>取值：</b>文本 <code>'abc'</code> 或 <code>\"abc\"</code>、数字 <code>123</code>、"
                    + "<code>true</code> / <code>false</code><br/>" +
            "<b>比较：</b><code>==</code> <code>!=</code> 判断相等，文本区分大小写，"
                    + "一边是数字时按数值比较（<code>${appId} == 100017451</code>）；"
                    + "<code>&gt;</code> <code>&gt;=</code> <code>&lt;</code> <code>&lt;=</code> 按数字比较大小<br/>" +
            "<b>集合：</b><code>in ('a', 'b')</code>、<code>not in ('a', 'b')</code>，列表可以换行<br/>" +
            "<b>文本：</b><code>contains</code> 包含、<code>startsWith</code> 前缀、<code>endsWith</code> 后缀、"
                    + "<code>matches</code> 正则（需整串匹配）；前面加 <code>not</code> 取反，如 <code>not contains</code><br/>" +
            "<b>逻辑：</b><code>&amp;&amp;</code> 或 <code>and</code>、<code>||</code> 或 <code>or</code>、"
                    + "<code>!</code> 或 <code>not</code>；<code>&amp;&amp;</code> 先于 <code>||</code> 计算，可用括号分组<br/>" +
            "<b>函数：</b><code>lower(s)</code> <code>upper(s)</code> <code>trim(s)</code> <code>length(s)</code> "
                    + "<code>substring(s, 开始[, 结束])</code>（下标从 0 开始）；函数直接写，不要用 <code>${}</code> 包裹<br/>" +
            "<b>转义：</b>字符串中 <code>\\'</code> <code>\\\"</code> <code>\\\\</code> 表示引号和反斜杠，"
                    + "其他反斜杠原样保留，正则可直接写 <code>'\\d+'</code><br/>" +
            "<b>其他：</b>关键字、函数名不区分大小写；表达式可以换行<br/><br/>" +
            "<b>示例：</b><br/>" +
            EXAMPLES.stream()
                    .map(example -> "• <code>" + escapeHtml(example) + "</code>")
                    .collect(Collectors.joining("<br/>"));

    private RouteExpressionHelp() {
    }

    /**
     * 独立展示用的完整 HTML
     */
    static String standaloneHtml(int widthPx) {
        return "<html><body style='width: " + widthPx + "px'>" + SECTION + "</body></html>";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
