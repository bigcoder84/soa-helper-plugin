package cn.bigcoder.soa.helper.settings;

import cn.bigcoder.soa.helper.ui.SoaHelperSettingsComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * SOA Helper 设置页面
 */
public class SoaHelperSettingsConfigurable implements Configurable {

    private static final Logger LOG = Logger.getInstance(SoaHelperSettingsConfigurable.class);
    
    private SoaHelperSettingsComponent settingsComponent;
    
    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "SOA Helper";
    }
    
    @Nullable
    @Override
    public JComponent createComponent() {
        settingsComponent = new SoaHelperSettingsComponent();
        return settingsComponent.getPanel();
    }
    
    @Override
    public boolean isModified() {
        SoaHelperSettings settings = SoaHelperSettings.getInstance();
        return settingsComponent.isModified(settings);
    }
    
    @Override
    public void apply() throws ConfigurationException {
        // 验证扩展字段配置
        if (settingsComponent.isExtendedFieldsEnabled()) {
            if (settingsComponent.getMomBaseUrl() == null 
                    || settingsComponent.getMomBaseUrl().trim().isEmpty()) {
                throw new ConfigurationException("启用扩展字段时，契约平台地址不能为空");
            }
            if (settingsComponent.getMomAccessToken() == null 
                    || settingsComponent.getMomAccessToken().trim().isEmpty()) {
                throw new ConfigurationException("启用扩展字段时，Access Token 不能为空");
            }
        }

        SoaHelperSettings settings = SoaHelperSettings.getInstance();
        settingsComponent.apply(settings);
        LOG.info("SOA settings applied: enabled=" + settings.isEnabled()
                + ", extendedFieldsEnabled=" + settings.isExtendedFieldsEnabled()
                + ", momBaseUrl=" + safeValue(settings.getMomBaseUrl())
                + ", tokenConfigured=" + (settings.getMomAccessToken() != null && !settings.getMomAccessToken().isEmpty())
                + ", timeoutMs=" + settings.getMomTimeout()
                + ", cacheTtlSec=" + settings.getMomCacheTtl());
    }
    
    @Override
    public void reset() {
        SoaHelperSettings settings = SoaHelperSettings.getInstance();
        settingsComponent.reset(settings);
    }
    
    @Override
    public void disposeUIResources() {
        settingsComponent = null;
    }

    private static String safeValue(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        return value;
    }
}
