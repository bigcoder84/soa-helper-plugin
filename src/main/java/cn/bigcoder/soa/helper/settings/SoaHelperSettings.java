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
    
    public SoaHelperSettings() {
        // 初始化默认配置
        initDefaultOptions();
    }
    
    /**
     * 初始化默认跳转选项
     */
    private void initDefaultOptions() {
        if (jumpOptions.isEmpty()) {
            // FAT 环境 Clog
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
}

