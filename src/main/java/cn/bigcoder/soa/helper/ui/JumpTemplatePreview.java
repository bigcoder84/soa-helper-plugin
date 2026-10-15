package cn.bigcoder.soa.helper.ui;

import cn.bigcoder.soa.helper.util.TemplateParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * SOA 跳转模板预览：用示例变量渲染模板，供编辑对话框展示
 */
final class JumpTemplatePreview {

    static final String SAMPLE_APP_ID = "12345";
    static final String SAMPLE_METHOD_NAME = "getUserInfo";

    private static final Set<String> EXTENDED_VAR_NAMES = Set.of("momProjectId", "momVersion", "serviceCode");

    private JumpTemplatePreview() {
    }

    /**
     * 渲染模板：基础变量（appId、methodName）由调用方提供，扩展变量使用示例值
     */
    static String render(String template, Map<String, String> variables) {
        Map<String, String> sampleVars = new HashMap<>();
        sampleVars.put("momProjectId", "67890");
        sampleVars.put("momVersion", "3");
        sampleVars.put("serviceCode", "sample.service");
        sampleVars.putAll(variables);
        return new TemplateParser().parse(template, sampleVars);
    }

    /**
     * 模板是否使用了扩展变量（需要启用扩展字段功能）
     */
    static boolean usesExtendedVars(String template) {
        if (template == null) {
            return false;
        }
        return EXTENDED_VAR_NAMES.stream().anyMatch(v -> template.contains("${" + v + "}"));
    }
}
