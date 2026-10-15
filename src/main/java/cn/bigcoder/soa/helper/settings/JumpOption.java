package cn.bigcoder.soa.helper.settings;

import cn.bigcoder.soa.helper.expression.RouteExpressionException;
import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * 跳转选项配置
 */
public class JumpOption {

    private static final Logger LOG = Logger.getInstance(JumpOption.class);

    /**
     * 跳转项名称
     */
    private String name;

    /**
     * URL模板，支持变量和函数；配置了路由时作为兜底模板
     * 示例：https://example.com?appId=${appId}&method=${lower(methodName)}
     */
    private String urlTemplate;

    /**
     * 是否启用该选项
     */
    private boolean enabled;

    /**
     * 路由列表，按顺序匹配，第一条条件成立的路由生效；都不成立时使用 urlTemplate
     */
    private List<JumpRoute> routes;

    public JumpOption() {
        this.enabled = true;
        this.routes = new ArrayList<>();
    }

    public JumpOption(String name, String urlTemplate) {
        this(name, urlTemplate, true);
    }

    public JumpOption(String name, String urlTemplate, boolean enabled) {
        this(name, urlTemplate, enabled, null);
    }

    public JumpOption(String name, String urlTemplate, boolean enabled, List<JumpRoute> routes) {
        this.name = name;
        this.urlTemplate = urlTemplate;
        this.enabled = enabled;
        this.routes = new ArrayList<>();
        if (routes != null) {
            for (JumpRoute route : routes) {
                this.routes.add(route.copy());
            }
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public List<JumpRoute> getRoutes() {
        return routes;
    }

    public void setRoutes(List<JumpRoute> routes) {
        this.routes = routes != null ? routes : new ArrayList<>();
    }

    /**
     * 根据变量（appId、methodName）选择实际使用的 URL 模板，出错的路由记录告警后跳过
     *
     * @see #resolve(Map, BiConsumer)
     */
    public ResolvedJump resolve(Map<String, String> variables) {
        return resolve(variables, (route, error) -> LOG.warn("Jump route skipped: option=" + name
                + ", route=" + route.getName() + ", condition=" + route.getCondition()
                + ", reason=" + error.getMessage()));
    }
    
    /**
     * 根据变量（appId、methodName）选择实际使用的 URL 模板：
     * 第一条条件成立的路由生效，都不成立时使用兜底模板。
     * 模板为空的路由直接跳过；条件非法或求值出错的路由交给 onRouteError 处理后跳过。
     */
    public ResolvedJump resolve(Map<String, String> variables,
                                BiConsumer<JumpRoute, RouteExpressionException> onRouteError) {
        for (JumpRoute route : routes) {
            String routeTemplate = route.getUrlTemplate();
            if (routeTemplate == null || routeTemplate.isBlank()) {
                continue;
            }
            try {
                if (route.matches(variables)) {
                    String routeName = route.getName() != null ? route.getName() : "";
                    return new ResolvedJump(name, routeTemplate, routeName);
                }
            } catch (RouteExpressionException e) {
                onRouteError.accept(route, e);
            }
        }
        return new ResolvedJump(name, urlTemplate, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JumpOption that = (JumpOption) o;
        return enabled == that.enabled &&
                Objects.equals(name, that.name) &&
                Objects.equals(urlTemplate, that.urlTemplate) &&
                Objects.equals(routes, that.routes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, urlTemplate, enabled, routes);
    }

    /**
     * 创建副本（路由深拷贝）
     */
    public JumpOption copy() {
        return new JumpOption(this.name, this.urlTemplate, this.enabled, this.routes);
    }
}
