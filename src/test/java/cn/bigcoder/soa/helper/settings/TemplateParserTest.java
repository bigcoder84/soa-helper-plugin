package cn.bigcoder.soa.helper.settings;

import cn.bigcoder.soa.helper.util.TemplateParser;
import java.util.HashMap;
import java.util.Map;

public class TemplateParserTest {

    public static void main(String[] args) {
        TemplateParser parser = new TemplateParser();

        Map<String, String> variables = new HashMap<>();
        variables.put("appId", "100017451");
        variables.put("params_0", "getUserInfo");

        System.out.println("=== 用户实际场景测试 ===\n");

        String userTemplate = "https://bat.fx.ctripcorp.com/clog?orgId=0&left=${urlEncode({\"datasource\":\"Clog-ClickHouse\",\"queries\":[{\"refId\":\"A\",\"table\":\"fx_log_${appId}_all\",\"where\":\"log_level IN ('INFO','WARN','ERROR','FATAL') AND log_level IN ('ERROR','FATAL','WARN','INFO') AND title = 'pro_${params_0}'\",\"scenario\":\"${appId}\",\"sqlEdit\":false,\"clogQueryModel\":{\"logLevels\":{\"INFO\":true,\"WARN\":true,\"ERROR\":true,\"FATAL\":true}},\"_where\":\"log_level IN ('INFO','WARN','ERROR','FATAL')\",\"whereQuery\":\"log_level IN ('ERROR','FATAL','WARN','INFO') AND title = 'pro_${params_0}'\",\"database\":\"log\",\"internalAdhocFilters\":[],\"eliminateAllSources\":false}],\"range\":{\"from\":\"now-1h\",\"to\":\"now\"},\"region\":\"UNKNOWN\"})}&leftHitName=${appId}&query=";

        System.out.println("模板长度: " + userTemplate.length());
        String result = parser.parse(userTemplate, variables);
        System.out.println("结果: " + result);
        System.out.println("结果长度: " + result.length());
        System.out.println();

        int leftStart = result.indexOf("left=");
        int leftEnd = result.indexOf("&", leftStart + 1);
        if (leftEnd == -1) leftEnd = result.length();
        String leftValue = result.substring(leftStart + 5, leftEnd);
        System.out.println("left参数值: " + leftValue);
        System.out.println("left参数值长度: " + leftValue.length());
        System.out.println("left参数值是否包含'datasource': " + leftValue.contains("datasource"));
        System.out.println("left参数值是否包含'Clog-ClickHouse': " + leftValue.contains("Clog-ClickHouse"));
        System.out.println("left参数值是否包含'table': " + leftValue.contains("table"));
        System.out.println("left参数值是否包含'urlEncode': " + leftValue.contains("urlEncode"));
        System.out.println();
    }
}
