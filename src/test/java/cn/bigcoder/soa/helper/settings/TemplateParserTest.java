package cn.bigcoder.soa.helper.settings;

import cn.bigcoder.soa.helper.util.TemplateParser;
import java.util.HashMap;
import java.util.Map;

/**
 * 模板解析器示例和测试
 * 演示新的简化语法的使用方法
 */
public class TemplateParserTest {
    
    public static void main(String[] args) {
        TemplateParser parser = new TemplateParser();
        
        // 准备测试数据
        Map<String, String> variables = new HashMap<>();
        variables.put("appId", "100012345");
        variables.put("methodName", "getUserInfo");
        
        System.out.println("=== 模板解析器测试（新语法） ===\n");
        
        // 测试1: 简单变量替换
        testTemplate(parser, variables, 
            "1. 简单变量替换",
            "https://example.com?appId=$appId&method=$methodName",
            "https://example.com?appId=100012345&method=getUserInfo"
        );
        
        // 测试2: 函数套参数
        testTemplate(parser, variables,
            "2. 大写转换",
            "https://example.com?method=$upper($methodName)",
            "https://example.com?method=GETUSERINFO"
        );
        
        // 测试3: 大写 + appId
        testTemplate(parser, variables,
            "3. 大写转换 appId",
            "https://example.com?app=$upper($appId)",
            "https://example.com?app=100012345"
        );
        

        // 测试16: 引号内的变量替换 - 使用 ${} 明确边界
        testTemplate(parser, variables,
            "16. 引号内支持变量替换（使用${} 明确边界）",
            "$upper(\"prefix_${methodName}_suffix\")",
            "PREFIX_GETUSERINFO_SUFFIX"
        );
        
        // 测试17: 引号转义
        testTemplate(parser, variables,
            "17. 引号内的转义字符",
            "$upper(\"text with \\\"quotes\\\" inside\")",
            "TEXT WITH \"QUOTES\" INSIDE"
        );
        
        // 测试18: 单引号
        testTemplate(parser, variables,
            "18. 使用单引号",
            "$upper('text(with)parens')",
            "TEXT(WITH)PARENS"
        );
        
        // 测试19: 复杂的实际场景 - 引号 + JSON + 变量
        testTemplate(parser, variables,
            "19. 复杂场景：引号 + JSON + 变量",
            "$upper(aa\")aa\")",
            "AAAA"
        );
        
        System.out.println("\n=== 所有测试完成 ===");
        System.out.println("\n支持的函数：");
        for (String func : parser.getSupportedFunctions()) {
            System.out.println("  - " + func + "()");
        }
        
        System.out.println("\n新语法的关键优势：");
        System.out.println("  ✓ 不需要 ${} 包裹，直接使用 $");
        System.out.println("  ✓ 不需要字符串拼接的 + 操作");
        System.out.println("  ✓ 简单场景不需要引号");
        System.out.println("  ✓ 函数参数可以包含任意字符");
        System.out.println("  ✓ 支持在参数中嵌套使用变量");
        System.out.println("  ✓ 支持引号明确边界，消除二义性");
    }
    
    /**
     * 测试模板解析
     */
    private static void testTemplate(TemplateParser parser, Map<String, String> variables, 
                                     String testName, String template, String expected) {
        System.out.println(testName);
        System.out.println("模板: " + template);
        
        String result = parser.parse(template, variables);
        System.out.println("结果: " + result);
        
        if (expected != null) {
            boolean passed = result.equals(expected);
            if (!passed) {
                System.out.println("❌ 测试失败！");
                System.out.println("预期: " + expected);
                System.out.println("实际: " + result);
                System.out.println("差异: 预期长度=" + expected.length() + ", 实际长度=" + result.length());
                throw new RuntimeException("测试失败: " + testName);
            } else {
                System.out.println("✓ 通过");
            }
        } else {
            System.out.println("⊙ 无验证（expected=null）");
        }
        System.out.println();
    }
}
