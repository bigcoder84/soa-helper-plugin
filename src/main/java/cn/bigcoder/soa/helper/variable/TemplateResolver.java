package cn.bigcoder.soa.helper.variable;

import cn.bigcoder.soa.helper.util.TemplateParser;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板解析编排器 —— 实现分组懒加载。
 *
 * 流程：
 * 1. 用基础变量做第一轮渲染
 * 2. 扫描渲染结果中剩余的 ${...} 占位符
 * 3. 通过 Registry 找到这些占位符所属的 VariableGroup（去重）
 * 4. 只调用需要的 Group 的 resolve()
 * 5. 合并新变量，做第二轮渲染
 */
public class TemplateResolver {

    /**
     * 匹配纯变量占位符 ${name}，不匹配函数调用 ${name(...)}
     * 注意：也需要匹配嵌套在函数参数中的变量，如 ${lower(${projectId})} 中的 projectId
     */
    private static final Pattern UNRESOLVED_VAR_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    private final VariableGroupRegistry registry;
    private final TemplateParser parser = new TemplateParser();

    public TemplateResolver(VariableGroupRegistry registry) {
        this.registry = registry;
    }

    /**
     * 解析模板，按需懒加载变量组。
     *
     * @param template URL 模板
     * @param basicVars 基础变量（appId, methodName）
     * @return 完全解析后的 URL
     * @throws VariableResolveException 某个 Group 解析失败时抛出
     */
    public String resolve(String template, Map<String, String> basicVars)
            throws VariableResolveException {

        // 第一轮：用基础变量渲染
        String firstPass = parser.parse(template, basicVars);

        // 扫描剩余未解析的变量名
        Set<String> unresolvedVars = extractUnresolvedVariables(firstPass);

        if (unresolvedVars.isEmpty()) {
            return firstPass;
        }

        // 找到需要调用的 Group（去重）
        Set<VariableGroup> neededGroups = registry.findGroupsForVariables(unresolvedVars);

        if (neededGroups.isEmpty()) {
            // 未解析的变量不属于任何已注册的 Group，返回第一轮结果
            return firstPass;
        }

        // 逐个 Group 解析，收集新变量
        Map<String, String> allVars = new HashMap<>(basicVars);
        for (VariableGroup group : neededGroups) {
            if (!group.isAvailable()) {
                throw new VariableResolveException(
                        "模板使用了扩展变量，请在设置中启用扩展字段功能并完成配置");
            }
            Map<String, String> resolved = group.resolve(allVars);
            allVars.putAll(resolved);
        }

        // 第二轮：用完整变量表渲染
        return parser.parse(template, allVars);
    }

    /**
     * 检查模板是否有 Group 需要异步加载。
     * 用于在跳转前判断是否需要弹出进度对话框。
     */
    public boolean needsAsyncResolve(String template, Map<String, String> basicVars) {
        String firstPass = parser.parse(template, basicVars);
        Set<String> unresolvedVars = extractUnresolvedVariables(firstPass);

        if (unresolvedVars.isEmpty()) {
            return false;
        }

        Set<VariableGroup> neededGroups = registry.findGroupsForVariables(unresolvedVars);
        for (VariableGroup group : neededGroups) {
            if (group.needsAsyncResolve(basicVars)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从渲染结果中提取未解析的变量名。
     * 匹配 ${variableName} 模式，排除已知的内置函数名。
     */
    public static Set<String> extractUnresolvedVariables(String text) {
        Set<String> result = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return result;
        }

        // 内置函数名不算未解析变量
        Set<String> builtinFunctions = Set.of(
                "lower", "upper", "urlEncode", "urlDecode", "base64Encode", "base64Decode");

        Matcher matcher = UNRESOLVED_VAR_PATTERN.matcher(text);
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!builtinFunctions.contains(varName)) {
                result.add(varName);
            }
        }
        return result;
    }
}
