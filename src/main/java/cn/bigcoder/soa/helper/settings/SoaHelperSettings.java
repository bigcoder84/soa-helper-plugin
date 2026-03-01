package cn.bigcoder.soa.helper.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SOA Helper 插件设置
 */
@State(
    name = "cn.bigcoder.soa.helper.settings.SoaHelperSettings",
    storages = @Storage("SoaHelperSettings.xml")
)
public class SoaHelperSettings implements PersistentStateComponent<SoaHelperSettings> {
    
    /**
     * 总开关：是否启用 SOA 方法跳转功能
     */
    private boolean enabled = true;
    
    private List<JumpOption> jumpOptions = new ArrayList<>();
    
    private List<LogJumpOption> logJumpOptions = new ArrayList<>();
    
    /**
     * 扩展字段开关
     */
    private boolean extendedFieldsEnabled = false;

    /**
     * MOM 契约平台 API 基础 URL
     */
    private String momBaseUrl = "";

    /**
     * MOM 契约平台 Access Token
     */
    private String momAccessToken = "";

    /**
     * API 请求超时时间（毫秒）
     */
    private int momTimeout = 5000;

    /**
     * 缓存 TTL（秒）
     */
    private int momCacheTtl = 300;
    
    public SoaHelperSettings() {
        // 初始化默认配置
        initDefaultOptions();
    }
    
    /**
     * 初始化默认跳转选项
     */
    private void initDefaultOptions() {
        if (jumpOptions.isEmpty()) {
            jumpOptions.add(new JumpOption(
                "示例跳转",
                "https://www.example.com?appId=${appId}&methodName=${methodName}"
            ));
        }
    }
    
    public static SoaHelperSettings getInstance() {
        return ApplicationManager.getApplication().getService(SoaHelperSettings.class);
    }
    
    @Nullable
    @Override
    public SoaHelperSettings getState() {
        return this;
    }
    
    @Override
    public void loadState(@NotNull SoaHelperSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public List<JumpOption> getJumpOptions() {
        return jumpOptions;
    }
    
    public void setJumpOptions(List<JumpOption> jumpOptions) {
        this.jumpOptions = jumpOptions;
    }
    
    /**
     * 获取所有启用的跳转选项
     */
    public List<JumpOption> getEnabledJumpOptions() {
        List<JumpOption> enabled = new ArrayList<>();
        for (JumpOption option : jumpOptions) {
            if (option.isEnabled()) {
                enabled.add(option);
            }
        }
        return enabled;
    }
    
    public List<LogJumpOption> getLogJumpOptions() {
        return logJumpOptions;
    }
    
    public void setLogJumpOptions(List<LogJumpOption> logJumpOptions) {
        this.logJumpOptions = logJumpOptions;
    }
    
    public boolean isExtendedFieldsEnabled() {
        return extendedFieldsEnabled;
    }

    public void setExtendedFieldsEnabled(boolean extendedFieldsEnabled) {
        this.extendedFieldsEnabled = extendedFieldsEnabled;
    }

    public String getMomBaseUrl() {
        return momBaseUrl;
    }

    public void setMomBaseUrl(String momBaseUrl) {
        this.momBaseUrl = momBaseUrl;
    }

    public String getMomAccessToken() {
        return momAccessToken;
    }

    public void setMomAccessToken(String momAccessToken) {
        this.momAccessToken = momAccessToken;
    }

    public int getMomTimeout() {
        return momTimeout;
    }

    public void setMomTimeout(int momTimeout) {
        this.momTimeout = momTimeout;
    }

    public int getMomCacheTtl() {
        return momCacheTtl;
    }

    public void setMomCacheTtl(int momCacheTtl) {
        this.momCacheTtl = momCacheTtl;
    }

    /**
     * 获取所有启用的日志跳转选项
     */
    public List<LogJumpOption> getEnabledLogJumpOptions() {
        List<LogJumpOption> enabled = new ArrayList<>();
        for (LogJumpOption option : logJumpOptions) {
            if (option.isEnabled()) {
                enabled.add(option);
            }
        }
        return enabled;
    }
}

