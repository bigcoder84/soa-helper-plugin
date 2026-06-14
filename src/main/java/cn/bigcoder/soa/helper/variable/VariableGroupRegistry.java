package cn.bigcoder.soa.helper.variable;

import java.util.*;

/**
 * 变量组注册表 —— 管理所有 VariableGroup，
 * 提供"根据变量名找到所属 Group"的能力。
 */
public class VariableGroupRegistry {

    private final List<VariableGroup> groups = new ArrayList<>();

    public void register(VariableGroup group) {
        groups.add(group);
    }

    /**
     * 给定一组未解析的变量名，返回需要调用的 VariableGroup 集合（去重）。
     * 如果某个变量名不属于任何已注册的 Group，则忽略它。
     */
    public Set<VariableGroup> findGroupsForVariables(Set<String> unresolvedVarNames) {
        Set<VariableGroup> result = new LinkedHashSet<>();
        for (String varName : unresolvedVarNames) {
            for (VariableGroup group : groups) {
                if (group.getVariableNames().contains(varName)) {
                    result.add(group);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 获取所有已注册的 Group
     */
    public List<VariableGroup> getAllGroups() {
        return Collections.unmodifiableList(groups);
    }
}
