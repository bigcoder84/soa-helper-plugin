package cn.bigcoder.soa.helper.expression;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class RouteExpressionTest {

    private static final Map<String, String> VARS = Map.of(
            "appId", "100017451",
            "methodName", "getUserInfo");

    private static boolean eval(String expression) {
        return RouteExpression.parse(expression).evaluate(VARS);
    }

    @Nested
    @DisplayName("求值")
    class Evaluate {

        @Test
        @DisplayName("in / not in 判断是否在列表中")
        void in_matchesListMembership() {
            assertThat(eval("${appId} in (\"100017451\",\"100017452\")")).isTrue();
            assertThat(eval("${appId} in (\"100017452\", \"100017453\")")).isFalse();
            assertThat(eval("${appId} not in (\"100017452\")")).isTrue();
            assertThat(eval("${appId} not in (\"100017451\")")).isFalse();
        }

        @Test
        @DisplayName("in 列表支持数字、变量和函数，允许末尾逗号与换行")
        void inList_acceptsAnyValueAndTrailingComma() {
            assertThat(eval("${appId} in (100017452, 100017451)")).isTrue();
            assertThat(eval("'getUserInfo' in (${appId}, ${methodName})")).isTrue();
            assertThat(eval("lower(${methodName}) in ('getuserinfo')")).isTrue();
            assertThat(eval("${appId} in (\n  \"100017450\",\n  \"100017451\",\n)\n")).isTrue();
        }

        @Test
        @DisplayName("== 与 != 做文本比较，区分大小写；两边都是数字时按数值比较")
        void equality_textOrNumeric() {
            assertThat(eval("${appId} == \"100017451\"")).isTrue();
            assertThat(eval("${appId} == 100017451")).isTrue();
            assertThat(eval("${appId} != '100017451'")).isFalse();
            assertThat(eval("${methodName} == 'getuserinfo'")).isFalse();
            assertThat(eval("1.0 == 1")).isTrue();
            assertThat(eval("true == true")).isTrue();
        }

        @Test
        @DisplayName("一边是数字、另一边是数字文本时按数值比较；两边都是文本时按文本比较")
        void equality_numberAgainstNumericText() {
            assertThat(eval("${appId} == 100017451.0")).isTrue();
            assertThat(eval("length(${appId}) == '9'")).isTrue();
            assertThat(RouteExpression.parse("${appId} in (0123)").evaluate(Map.of("appId", "0123"))).isTrue();
            assertThat(eval("'0123' == '123'")).isFalse();
            assertThat(eval("${methodName} == 1")).isFalse();
        }

        @Test
        @DisplayName("> >= < <= 按数字比较，变量文本会转成数字")
        void relational_comparesNumerically() {
            assertThat(eval("${appId} >= 100017000 && ${appId} < 100018000")).isTrue();
            assertThat(eval("${appId} > 100017451")).isFalse();
            assertThat(eval("${appId} <= '100017451'")).isTrue();
            assertThat(eval("-1 < 0")).isTrue();
            // 按数字而非字典序：9 < 10
            assertThat(eval("'9' < '10'")).isTrue();
        }

        @Test
        @DisplayName("大小比较遇到非数字时求值报错")
        void relational_nonNumeric_throws() {
            assertThatThrownBy(() -> eval("${methodName} > 1"))
                    .isInstanceOf(RouteExpressionException.class)
                    .hasMessageContaining("不是数字");
        }

        @Test
        @DisplayName("contains / startsWith / endsWith 及其 not 形式")
        void textPredicates() {
            assertThat(eval("${appId} startsWith '1000174'")).isTrue();
            assertThat(eval("${appId} endsWith '451'")).isTrue();
            assertThat(eval("${methodName} contains 'User'")).isTrue();
            assertThat(eval("${methodName} not contains 'Order'")).isTrue();
            assertThat(eval("${appId} not startsWith '1000174'")).isFalse();
            assertThat(eval("${appId} startsWith 1000")).isTrue();
        }

        @Test
        @DisplayName("matches 使用正则整串匹配，支持 not matches")
        void matches_fullRegexMatch() {
            assertThat(eval("${methodName} matches 'get.*'")).isTrue();
            assertThat(eval("${methodName} matches 'get'")).isFalse();
            assertThat(eval("${appId} matches '\\d{9}'")).isTrue();
            assertThat(eval("${appId} not matches '1000\\d+'")).isFalse();
        }

        @Test
        @DisplayName("matches 右侧为变量时运行期编译正则，非法正则报错")
        void matches_dynamicPattern() {
            Map<String, String> vars = Map.of("appId", "abc", "methodName", "a.c");
            assertThat(RouteExpression.parse("${appId} matches ${methodName}").evaluate(vars)).isTrue();

            Map<String, String> badVars = Map.of("appId", "abc", "methodName", "[");
            assertThatThrownBy(() -> RouteExpression.parse("${appId} matches ${methodName}").evaluate(badVars))
                    .isInstanceOf(RouteExpressionException.class)
                    .hasMessageContaining("正则");
        }

        @Test
        @DisplayName("函数 lower / upper / trim / length / substring")
        void builtinFunctions() {
            assertThat(eval("lower(${methodName}) == 'getuserinfo'")).isTrue();
            assertThat(eval("upper(${methodName}) == 'GETUSERINFO'")).isTrue();
            assertThat(eval("trim('  x ') == 'x'")).isTrue();
            assertThat(eval("length(${appId}) == 9")).isTrue();
            assertThat(eval("substring(${appId}, 0, 4) == '1000'")).isTrue();
            assertThat(eval("substring(${appId}, 6) == '451'")).isTrue();
            assertThat(eval("LOWER(upper(${methodName})) == 'getuserinfo'")).isTrue();
        }

        @Test
        @DisplayName("substring 下标越界时自动截断而不报错")
        void substring_clampsIndexes() {
            assertThat(eval("substring('abc', 1, 99) == 'bc'")).isTrue();
            assertThat(eval("substring('abc', -5, 1) == 'a'")).isTrue();
            assertThat(eval("substring('abc', 2, 1) == ''")).isTrue();
            assertThat(eval("substring('abc', 0, 4294967297) == 'abc'")).isTrue();
            assertThat(eval("substring('abc', 1.9) == 'bc'")).isTrue();
        }

        @Test
        @DisplayName("&& || ! 组合，&& 优先级高于 ||")
        void logicalOperators() {
            assertThat(eval("${appId} == '100017451' && ${methodName} == 'getUserInfo'")).isTrue();
            assertThat(eval("${appId} == 'x' || ${methodName} == 'getUserInfo'")).isTrue();
            assertThat(eval("!(${appId} == '100017451')")).isFalse();
            assertThat(eval("!${appId} in ('x')")).isTrue();
            // true || (false && false) = true；若从左到右计算则为 false
            assertThat(eval("${appId} == '100017451' || ${appId} == 'x' && ${appId} == 'y'")).isTrue();
            assertThat(eval("(${appId} == '100017451' || ${appId} == 'x') && ${appId} == 'y'")).isFalse();
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("关键字不区分大小写，and / or / not 与符号等价")
        @MethodSource("cn.bigcoder.soa.helper.expression.RouteExpressionTest#keywordCases")
        void keywords_caseInsensitive(String expression, boolean expected) {
            assertThat(eval(expression)).isEqualTo(expected);
        }

        @Test
        @DisplayName("! 与 not 一样可用于 in / contains 等取反")
        void bangBeforeWordOperator_negates() {
            assertThat(eval("${appId} !in ('x')")).isTrue();
            assertThat(eval("${methodName} !contains 'User'")).isFalse();
        }

        @Test
        @DisplayName("从网页、聊天粘贴来的不间断空格、零宽字符视为空白")
        void pastedSpecialSpaces_treatedAsWhitespace() {
            assertThat(eval("${appId}\u00A0in\u200B('100017451')\uFEFF")).isTrue();
        }

        @Test
        @DisplayName("变量可以省略 ${}")
        void bareIdentifier_isVariable() {
            assertThat(eval("appId in ('100017451') and methodName startsWith 'get'")).isTrue();
        }

        @Test
        @DisplayName("true / false 字面量")
        void booleanLiterals() {
            assertThat(eval("true")).isTrue();
            assertThat(eval("false || ${appId} == '100017451'")).isTrue();
        }

        @Test
        @DisplayName("字符串转义：\\\" \\' \\\\ 转义，其他反斜杠原样保留（便于写正则）")
        void stringEscapes() {
            Map<String, String> vars = Map.of("appId", "a\"b'c\\d");
            assertThat(RouteExpression.parse("${appId} == \"a\\\"b'c\\\\d\"").evaluate(vars)).isTrue();
            assertThat(RouteExpression.parse("${appId} == 'a\"b\\'c\\\\d'").evaluate(vars)).isTrue();
        }

        @Test
        @DisplayName("变量值两端空白被忽略")
        void variableValue_trimmed() {
            Map<String, String> vars = Map.of("appId", " 100017451 ");
            assertThat(RouteExpression.parse("${appId} == '100017451'").evaluate(vars)).isTrue();
        }

        @Test
        @DisplayName("求值时变量缺失抛出异常")
        void missingVariable_throws() {
            RouteExpression expression = RouteExpression.parse("${env} == 'pro'");

            assertThatThrownBy(() -> expression.evaluate(VARS))
                    .isInstanceOf(RouteExpressionException.class)
                    .hasMessageContaining("env");
        }

        @Test
        @DisplayName("短路求值：|| 左侧成立、&& 左侧不成立时不再计算右侧")
        void logical_shortCircuits() {
            assertThat(eval("${appId} == '100017451' || ${env} == 'pro'")).isTrue();
            assertThat(eval("${appId} == 'x' && ${env} == 'pro'")).isFalse();
        }
    }

    @Nested
    @DisplayName("解析")
    class Parse {

        @Test
        @DisplayName("variableNames 返回表达式引用的全部变量")
        void variableNames_collectsAllReferences() {
            RouteExpression expression = RouteExpression.parse(
                    "${appId} in ('1') && (lower(methodName) == 'x' || !(${env} != 'pro'))");

            assertThat(expression.variableNames()).containsExactlyInAnyOrder("appId", "methodName", "env");
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("非法表达式给出可读的错误")
        @MethodSource("cn.bigcoder.soa.helper.expression.RouteExpressionTest#invalidCases")
        void invalidExpression_throwsReadableError(String expression, String expectedMessagePart) {
            assertThatThrownBy(() -> RouteExpression.parse(expression))
                    .isInstanceOf(RouteExpressionException.class)
                    .hasMessageContaining(expectedMessagePart);
        }

        @Test
        @DisplayName("嵌套过深时报错而不是栈溢出")
        void deepNesting_throwsReadableError() {
            String deepParens = "(".repeat(5000) + "${appId} == '1'" + ")".repeat(5000);
            String deepNots = "!".repeat(5000) + "${appId} == '1'";
            String deepFunctions = "lower(".repeat(5000) + "${appId}" + ")".repeat(5000) + " == '1'";

            for (String expression : new String[]{deepParens, deepNots, deepFunctions}) {
                assertThatThrownBy(() -> RouteExpression.parse(expression))
                        .isInstanceOf(RouteExpressionException.class)
                        .hasMessageContaining("嵌套过深");
            }
        }

        @Test
        @DisplayName("把 URL 模板里的函数写法 ${lower(...)} 用在条件里时给出提示")
        void templateStyleFunction_givesHint() {
            assertThatThrownBy(() -> RouteExpression.parse("${lower(${methodName})} == 'x'"))
                    .isInstanceOf(RouteExpressionException.class)
                    .hasMessageContaining("不要用 ${} 包裹");
        }

        @Test
        @DisplayName("多行表达式报告行号和列号")
        void multiLineExpression_reportsLineAndColumn() {
            RouteExpressionException e = org.junit.jupiter.api.Assertions.assertThrows(
                    RouteExpressionException.class,
                    () -> RouteExpression.parse("${appId} in (\n  '1',\n  '2' = \n)"));

            assertThat(e.getMessage()).startsWith("第 3 行第 7 列").contains("==");
            assertThat(e.getDetail()).doesNotContain("第");
        }

        @Test
        @DisplayName("null 视为空表达式")
        void nullExpression_throws() {
            assertThatThrownBy(() -> RouteExpression.parse(null))
                    .isInstanceOf(RouteExpressionException.class)
                    .hasMessageContaining("不能为空");
        }

        @Test
        @DisplayName("错误信息包含出错位置")
        void errorMessage_containsPosition() {
            RouteExpressionException e = org.junit.jupiter.api.Assertions.assertThrows(
                    RouteExpressionException.class, () -> RouteExpression.parse("${appId} = '1'"));

            assertThat(e.getMessage()).contains("第 10 个字符");
            assertThat(e.getPosition()).isEqualTo(9);
        }
    }

    static Stream<Arguments> keywordCases() {
        return Stream.of(
                arguments("${appId} IN ('100017451')", true),
                arguments("${appId} NOT IN ('100017451')", false),
                arguments("${appId} == '100017451' and ${methodName} == 'getUserInfo'", true),
                arguments("${appId} == 'x' OR ${appId} == '100017451'", true),
                arguments("not ${appId} == '100017451'", false),
                arguments("${appId} STARTSWITH '1000'", true),
                arguments("${methodName} Not Contains 'x'", true));
    }

    static Stream<Arguments> invalidCases() {
        return Stream.of(
                arguments("", "不能为空"),
                arguments("   ", "不能为空"),
                arguments("${appId} in ('1'", ")"),
                arguments("${appId} in ()", "不能为空"),
                arguments("${appId} in ${methodName}", "("),
                arguments("${appId} = '1'", "=="),
                arguments("${appId} == '1' & ${appId} == '2'", "&&"),
                arguments("${appId} == '1' | ${appId} == '2'", "||"),
                arguments("${appId}", "比较运算符"),
                arguments("${appId} == '1' || ${methodName}", "比较运算符"),
                arguments("!${appId}", "比较运算符"),
                arguments("${appId} == '1' ${appId}", "多余"),
                arguments("${appId == '1'", "}"),
                arguments("${appId} == '1", "引号"),
                arguments("(${appId} == '1'", ")"),
                arguments("${appId} == '1' &&", "结尾"),
                arguments("${1abc} == '1'", "变量名"),
                arguments("${appId} in （'1'）", "英文"),
                arguments("${appId} == “1”", "英文"),
                arguments("foo(${appId}) == '1'", "未知函数"),
                arguments("substring(${appId}) == '1'", "参数"),
                arguments("lower(${appId} == '1') == 'x'", "参数"),
                arguments("true > 1", "数字"),
                arguments("${appId} startsWith (${appId} == '1')", "文本"),
                arguments("${appId} matches '['", "正则"),
                arguments("${appId} == ", "结尾"),
                arguments("${appId} not == '1'", "多余"),
                arguments("${appId} == '1' @", "无法识别"));
    }
}
