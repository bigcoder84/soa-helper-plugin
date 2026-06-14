package cn.bigcoder.soa.helper.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 日志快速跳转选项配置
 */
public class LogJumpOption {
    /**
     * 跳转项名称
     */
    private String name;

    /**
     * 要匹配的方法模式列表，格式为 FullyQualifiedClassName#MethodName
     * 例如：cn.bigcoder.soa.helper.Clogger#info
     * 也支持简单类名：CLogger#Info
     */
    private List<String> methodPatterns;

    /**
     * URL模板，支持 ${appId}、${params[N]} 等变量
     * 示例：https://bigcoder.cn/xxx/${appId}/xxxx?filter=${params[0]}
     */
    private String urlTemplate;

    /**
     * 是否启用该选项
     */
    private boolean enabled;

    public LogJumpOption() {
        this.enabled = true;
        this.methodPatterns = new ArrayList<>();
    }

    public LogJumpOption(String name, List<String> methodPatterns, String urlTemplate, boolean enabled) {
        this.name = name;
        this.methodPatterns = methodPatterns != null ? new ArrayList<>(methodPatterns) : new ArrayList<>();
        this.urlTemplate = urlTemplate;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getMethodPatterns() {
        return methodPatterns;
    }

    public void setMethodPatterns(List<String> methodPatterns) {
        this.methodPatterns = methodPatterns != null ? methodPatterns : new ArrayList<>();
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
     * 获取方法模式的显示字符串（用换行符分隔）
     */
    public String getMethodPatternsDisplay() {
        if (methodPatterns == null || methodPatterns.isEmpty()) {
            return "";
        }
        return String.join("\n", methodPatterns);
    }

    /**
     * 从显示字符串设置方法模式（按换行符分割）
     */
    public void setMethodPatternsFromDisplay(String display) {
        methodPatterns = new ArrayList<>();
        if (display != null && !display.trim().isEmpty()) {
            for (String line : display.split("\\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    methodPatterns.add(trimmed);
                }
            }
        }
    }

    /**
     * 获取方法模式的简短显示（用逗号分隔，用于表格列）
     */
    public String getMethodPatternsShortDisplay() {
        if (methodPatterns == null || methodPatterns.isEmpty()) {
            return "";
        }
        return String.join(", ", methodPatterns);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogJumpOption that = (LogJumpOption) o;
        return enabled == that.enabled &&
                Objects.equals(name, that.name) &&
                Objects.equals(methodPatterns, that.methodPatterns) &&
                Objects.equals(urlTemplate, that.urlTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, methodPatterns, urlTemplate, enabled);
    }

    /**
     * 创建副本
     */
    public LogJumpOption copy() {
        return new LogJumpOption(this.name, this.methodPatterns, this.urlTemplate, this.enabled);
    }
}
