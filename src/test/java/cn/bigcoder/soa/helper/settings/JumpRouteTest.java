package cn.bigcoder.soa.helper.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JumpRouteTest {

    private static final String CK_TEMPLATE = "https://ck.example.com?app=${appId}";
    private static final Map<String, String> VARS = Map.of("appId", "100017451", "methodName", "getUserInfo");

    @Test
    @DisplayName("条件成立时 matches 返回 true")
    void matches_conditionTrue() {
        JumpRoute route = new JumpRoute("ClickHouse", "${appId} in ('100017451')", CK_TEMPLATE, true);

        assertThat(route.matches(VARS)).isTrue();
    }

    @Test
    @DisplayName("条件不成立时 matches 返回 false")
    void matches_conditionFalse() {
        JumpRoute route = new JumpRoute("ClickHouse", "${appId} in ('100017452')", CK_TEMPLATE, true);

        assertThat(route.matches(VARS)).isFalse();
    }

    @Test
    @DisplayName("停用的路由不匹配，且不解析条件")
    void matches_disabled_returnsFalseWithoutParsing() {
        JumpRoute route = new JumpRoute("ClickHouse", "not a valid ((", CK_TEMPLATE, false);

        assertThat(route.matches(VARS)).isFalse();
    }

    @Test
    @DisplayName("validateCondition：合法表达式无错误")
    void validateCondition_valid() {
        assertThat(JumpRoute.validateCondition("${appId} startsWith '1000' && methodName != 'ping'")).isEmpty();
    }

    @Test
    @DisplayName("validateCondition：语法错误返回带位置的信息")
    void validateCondition_syntaxError() {
        assertThat(JumpRoute.validateCondition("${appId} = '1'"))
                .hasValueSatisfying(message -> assertThat(message).contains("第 10 个字符").contains("=="));
    }

    @Test
    @DisplayName("validateCondition：引用了不支持的变量")
    void validateCondition_unknownVariable() {
        assertThat(JumpRoute.validateCondition("${env} == 'pro'"))
                .hasValueSatisfying(message -> assertThat(message).contains("env").contains("appId"));
    }

    @Test
    @DisplayName("validateCondition：空条件")
    void validateCondition_blank() {
        assertThat(JumpRoute.validateCondition("  ")).hasValueSatisfying(m -> assertThat(m).contains("不能为空"));
    }

    @Test
    @DisplayName("conditionVariables 构造的变量与可用变量列表一致")
    void conditionVariables_matchesDeclaredNames() {
        Map<String, String> variables = JumpRoute.conditionVariables("100017451", "getUserInfo");

        assertThat(variables.keySet()).containsExactlyInAnyOrderElementsOf(JumpRoute.CONDITION_VARIABLES);
        assertThat(variables).containsEntry("appId", "100017451").containsEntry("methodName", "getUserInfo");
    }

    @Test
    @DisplayName("copy 产生相等但独立的副本")
    void copy_equalButIndependent() {
        JumpRoute route = new JumpRoute("ClickHouse", "${appId} == '1'", CK_TEMPLATE, true);

        JumpRoute copy = route.copy();
        copy.setCondition("${appId} == '2'");

        assertThat(route.copy()).isEqualTo(route).isNotSameAs(route);
        assertThat(route.getCondition()).isEqualTo("${appId} == '1'");
    }

    @Test
    @DisplayName("equals 覆盖所有字段")
    void equals_comparesAllFields() {
        JumpRoute route = new JumpRoute("A", "${appId} == '1'", CK_TEMPLATE, true);

        assertThat(route).isEqualTo(new JumpRoute("A", "${appId} == '1'", CK_TEMPLATE, true));
        assertThat(route).isNotEqualTo(new JumpRoute("B", "${appId} == '1'", CK_TEMPLATE, true));
        assertThat(route).isNotEqualTo(new JumpRoute("A", "${appId} == '2'", CK_TEMPLATE, true));
        assertThat(route).isNotEqualTo(new JumpRoute("A", "${appId} == '1'", "other", true));
        assertThat(route).isNotEqualTo(new JumpRoute("A", "${appId} == '1'", CK_TEMPLATE, false));
    }
}
