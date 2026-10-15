package cn.bigcoder.soa.helper.expression;

/**
 * 路由条件表达式解析或求值失败
 */
public class RouteExpressionException extends RuntimeException {

    /**
     * 不含位置前缀的错误描述
     */
    private final String detail;

    /**
     * 出错位置（从 0 开始），无法定位时为 -1
     */
    private final int position;

    public RouteExpressionException(String detail) {
        this(detail, -1);
    }

    public RouteExpressionException(String detail, int position) {
        this(detail, position, position >= 0 ? "第 " + (position + 1) + " 个字符" : null);
    }

    private RouteExpressionException(String detail, int position, String location) {
        super(location != null ? location + "：" + detail : detail);
        this.detail = detail;
        this.position = position;
    }

    public String getDetail() {
        return detail;
    }

    public int getPosition() {
        return position;
    }

    /**
     * 多行表达式把位置换算成「第 L 行第 C 列」，单行表达式保持「第 N 个字符」
     */
    RouteExpressionException locate(String source) {
        if (position < 0 || source == null || source.indexOf('\n') < 0) {
            return this;
        }
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < position && i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        String location = "第 " + line + " 行第 " + (position - lineStart + 1) + " 列";
        return new RouteExpressionException(detail, position, location);
    }
}
