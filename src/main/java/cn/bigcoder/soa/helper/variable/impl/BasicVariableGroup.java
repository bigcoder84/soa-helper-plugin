package cn.bigcoder.soa.helper.variable.impl;

import cn.bigcoder.soa.helper.variable.VariableGroup;
import cn.bigcoder.soa.helper.variable.VariableResolveException;

import java.util.*;

/**
 * 基础变量组 —— 提供 appId 和 methodName。
 * 变量在构造时传入，resolve() 直接返回，无IO操作。
 */
public class BasicVariableGroup implements VariableGroup {

    private static final Set<String> VARIABLE_NAMES = Set.of("appId", "methodName");

    private final Map<String, String> variables;

    public BasicVariableGroup(String appId, String methodName) {
        this.variables = new HashMap<>();
        this.variables.put("appId", appId);
        this.variables.put("methodName", methodName);
    }

    @Override
    public Set<String> getVariableNames() {
        return VARIABLE_NAMES;
    }

    @Override
    public Map<String, String> resolve(Map<String, String> context) throws VariableResolveException {
        return new HashMap<>(variables);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean needsAsyncResolve(Map<String, String> context) {
        return false;
    }
}
