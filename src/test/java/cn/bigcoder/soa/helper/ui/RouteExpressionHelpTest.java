package cn.bigcoder.soa.helper.ui;

import cn.bigcoder.soa.helper.settings.JumpRoute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteExpressionHelpTest {

    @Test
    @DisplayName("面板上展示的每个示例都是合法的路由条件")
    void examples_areValidConditions() {
        assertThat(RouteExpressionHelp.EXAMPLES).isNotEmpty();
        for (String example : RouteExpressionHelp.EXAMPLES) {
            assertThat(JumpRoute.validateCondition(example)).as(example).isEmpty();
        }
    }

    @Test
    @DisplayName("示例在 HTML 中经过转义")
    void examples_areHtmlEscaped() {
        assertThat(RouteExpressionHelp.SECTION)
                .contains("&amp;&amp; ${methodName}")
                .contains("${appId} &gt;= 100017000");
    }
}
