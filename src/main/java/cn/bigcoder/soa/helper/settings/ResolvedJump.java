package cn.bigcoder.soa.helper.settings;

import org.jetbrains.annotations.Nullable;

/**
 * 跳转项的路由结果
 *
 * @param optionName       跳转项名称
 * @param urlTemplate      实际使用的 URL 模板（命中路由时为路由模板，否则为兜底模板）
 * @param matchedRouteName 命中的路由名称（路由未命名时为空串），未命中任何路由时为 null
 */
public record ResolvedJump(String optionName, String urlTemplate, @Nullable String matchedRouteName) {

    private static final String ROUTE_SUFFIX_SEPARATOR = " · ";

    /**
     * 是否命中了路由
     */
    public boolean routeMatched() {
        return matchedRouteName != null;
    }

    /**
     * 菜单显示名：命中有名称的路由时追加后缀，如 "入口日志 · ClickHouse"
     */
    public String displayName() {
        if (matchedRouteName == null || matchedRouteName.isBlank()) {
            return optionName;
        }
        return optionName + ROUTE_SUFFIX_SEPARATOR + matchedRouteName.trim();
    }
}
