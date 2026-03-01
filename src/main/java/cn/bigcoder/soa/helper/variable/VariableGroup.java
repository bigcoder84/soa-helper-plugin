package cn.bigcoder.soa.helper.variable;

import java.util.Map;
import java.util.Set;

/**
 * 变量组接口 —— 一组由同一数据源提供的模板变量。
 * 一个 Group 对应一个数据源（一次 API 调用 / 一次本地解析），
 * Group 内的变量要么整组加载，要么整组不加载。
 */
public interface VariableGroup {

    /**
     * 该组提供的所有变量名（如 {"projectId", "momVersion", "serviceCode"}）
     */
    Set<String> getVariableNames();

    /**
     * 一次性解析该组所有变量。
     *
     * @param context 上下文变量（已解析的变量，如 appId），供本组解析时参考
     * @return 变量名 → 变量值 的映射
     * @throws VariableResolveException 解析失败时抛出
     */
    Map<String, String> resolve(Map<String, String> context) throws VariableResolveException;

    /**
     * 该组是否可用（如：扩展字段开关是否开启、配置是否完整）
     */
    boolean isAvailable();

    /**
     * 检查该组在给定上下文下是否需要异步加载（即缓存未命中）。
     * 返回 true 表示 resolve() 可能涉及网络IO，调用方应在后台线程执行。
     * 返回 false 表示 resolve() 可以同步快速返回。
     */
    boolean needsAsyncResolve(Map<String, String> context);
}
