package cn.bigcoder.soa.helper.settings;

import cn.bigcoder.soa.helper.expression.RouteExpression;
import cn.bigcoder.soa.helper.expression.RouteExpressionException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 跳转项的一条路由：条件表达式成立时，使用本路由的 URL 模板
 * 条件示例：${appId} in ("100017451", "100017452")
 */
public class JumpRoute {

    /**
     * 条件表达式可用的变量，与 conditionVariables 保持一致
     */
    public static final List<String> CONDITION_VARIABLES = List.of("appId", "methodName");

    /**
     * 路由名称，命中时作为菜单后缀显示，如 "ClickHouse"
     */
    private String name;

    /**
     * 条件表达式
     */
    private String condition;

    /**
     * 条件成立时使用的 URL 模板，语法与跳转项模板一致
     */
    private String urlTemplate;

    /**
     * 是否启用该路由
     */
    private boolean enabled;

    public JumpRoute() {
        this.enabled = true;
    }

    public JumpRoute(String name, String condition, String urlTemplate, boolean enabled) {
        this.name = name;
        this.condition = condition;
        this.urlTemplate = urlTemplate;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getUrlTemplate() {
        return urlTemplate;
    }

    public void setUrlTemplate(String urlTemplate) {
        this.urlTemplate = urlTemplate;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 路由是否对给定变量生效（已启用且条件成立）
     *
     * @throws RouteExpressionException 条件语法错误或求值出错
     */
    public boolean matches(Map<String, String> variables) {
        return enabled && RouteExpression.parse(condition).evaluate(variables);
    }

    /**
     * 构造条件表达式求值所需的变量，跳转与界面预览统一使用此方法
     */
    public static Map<String, String> conditionVariables(String appId, String methodName) {
        return Map.of("appId", appId, "methodName", methodName);
    }

    /**
     * 校验条件表达式：语法正确且只引用了可用变量
     *
     * @return 错误信息；校验通过时为空
     */
    public static Optional<String> validateCondition(String condition) {
        try {
            RouteExpression expression = RouteExpression.parse(condition);
            for (String variable : expression.variableNames()) {
                if (!CONDITION_VARIABLES.contains(variable)) {
                    return Optional.of("未知变量 " + variable + "，可用变量：" + String.join("、", CONDITION_VARIABLES));
                }
            }
            return Optional.empty();
        } catch (RouteExpressionException e) {
            return Optional.of(e.getMessage());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JumpRoute that = (JumpRoute) o;
        return enabled == that.enabled &&
                Objects.equals(name, that.name) &&
                Objects.equals(condition, that.condition) &&
                Objects.equals(urlTemplate, that.urlTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, condition, urlTemplate, enabled);
    }

    /**
     * 创建副本
     */
    public JumpRoute copy() {
        return new JumpRoute(this.name, this.condition, this.urlTemplate, this.enabled);
    }
}
