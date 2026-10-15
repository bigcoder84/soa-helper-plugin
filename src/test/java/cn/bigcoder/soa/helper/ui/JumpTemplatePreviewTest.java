package cn.bigcoder.soa.helper.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JumpTemplatePreviewTest {

    @Test
    @DisplayName("render 使用调用方给的 appId / methodName，扩展变量使用示例值")
    void render_usesGivenVariablesAndSampleExtendedVars() {
        String url = JumpTemplatePreview.render(
                "https://x.com?app=${appId}&m=${lower(${methodName})}&p=${momProjectId}",
                Map.of("appId", "100017451", "methodName", "GetUser"));

        assertThat(url).isEqualTo("https://x.com?app=100017451&m=getuser&p=67890");
    }

    @Test
    @DisplayName("usesExtendedVars 识别扩展变量，null 安全")
    void usesExtendedVars() {
        assertThat(JumpTemplatePreview.usesExtendedVars("https://x.com?v=${momVersion}")).isTrue();
        assertThat(JumpTemplatePreview.usesExtendedVars("https://x.com?app=${appId}")).isFalse();
        assertThat(JumpTemplatePreview.usesExtendedVars(null)).isFalse();
    }
}
