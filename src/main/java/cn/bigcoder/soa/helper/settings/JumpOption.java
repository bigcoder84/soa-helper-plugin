package cn.bigcoder.soa.helper.settings;

import java.util.Objects;

/**
 * 跳转选项配置
 */
public class JumpOption {
    /**
     * 跳转项名称，例如：生产环境Clog、生产环境Bat
     */
    private String name;
    
    /**
     * URL模板，支持变量和函数
     * 示例：https://example.com?appId=${appId}&method=${lower(methodName)}
     */
    private String urlTemplate;
    
    /**
     * 是否启用该选项
     */
    private boolean enabled;
    
    public JumpOption() {
        this.enabled = true;
    }
    
    public JumpOption(String name, String urlTemplate) {
        this.name = name;
        this.urlTemplate = urlTemplate;
        this.enabled = true;
    }
    
    public JumpOption(String name, String urlTemplate, boolean enabled) {
        this.name = name;
        this.urlTemplate = urlTemplate;
        this.enabled = enabled;
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
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JumpOption that = (JumpOption) o;
        return enabled == that.enabled &&
                Objects.equals(name, that.name) &&
                Objects.equals(urlTemplate, that.urlTemplate);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, urlTemplate, enabled);
    }
    
    /**
     * 创建副本
     */
    public JumpOption copy() {
        return new JumpOption(this.name, this.urlTemplate, this.enabled);
    }
}

