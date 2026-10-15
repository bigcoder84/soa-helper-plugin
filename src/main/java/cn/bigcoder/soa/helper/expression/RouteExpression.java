package cn.bigcoder.soa.helper.expression;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 路由条件表达式，例如：${appId} in ("100017451", "100017452")
 * 自研的小型表达式语言，只做取值和比较，不执行任意代码。语法见 RouteExpressionParser。
 */
public final class RouteExpression {

    private final String source;
    private final ExpressionNode root;
    private final Set<String> variableNames;

    private RouteExpression(String source, ExpressionNode root) {
        this.source = source;
        this.root = root;
        Set<String> names = new LinkedHashSet<>();
        root.collectVariables(names);
        this.variableNames = Collections.unmodifiableSet(names);
    }

    /**
     * 解析表达式
     *
     * @throws RouteExpressionException 语法错误时抛出，信息中包含出错位置
     */
    public static RouteExpression parse(String source) {
        try {
            return new RouteExpression(source, RouteExpressionParser.parse(source));
        } catch (RouteExpressionException e) {
            throw e.locate(source);
        }
    }

    /**
     * 用给定变量求值
     *
     * @throws RouteExpressionException 变量缺失、数字转换失败等运行期错误
     */
    public boolean evaluate(Map<String, String> variables) {
        return ExpressionValues.asBoolean(root.evaluate(variables));
    }

    /**
     * 表达式引用的全部变量名
     */
    public Set<String> variableNames() {
        return variableNames;
    }

    public String source() {
        return source;
    }
}
