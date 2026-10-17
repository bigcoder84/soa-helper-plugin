package cn.bigcoder.soa.helper.settings;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JumpOptionTest {

    private static final String CLOG_TEMPLATE = "https://clog.example.com?app=${appId}&title=${methodName}";
    private static final String CK_TEMPLATE = "https://ck.example.com?app=${appId}&method=${methodName}";
    private static final String CK_B_TEMPLATE = "https://ck-b.example.com?app=${appId}";

    private static JumpOption entryLogOption(List<JumpRoute> routes) {
        return new JumpOption("入口日志", CLOG_TEMPLATE, true, routes);
    }

    private static Map<String, String> vars(String appId) {
        return Map.of("appId", appId, "methodName", "getUserInfo");
    }

    @Nested
    @DisplayName("resolve 按条件表达式路由")
    class Resolve {

        @Test
        @DisplayName("没有路由时走兜底模板，显示原名")
        void noRoutes_usesFallback() {
            JumpOption option = new JumpOption("入口日志", CLOG_TEMPLATE);

            ResolvedJump resolved = option.resolve(vars("100001"));

            assertThat(resolved.urlTemplate()).isEqualTo(CLOG_TEMPLATE);
            assertThat(resolved.routeMatched()).isFalse();
            assertThat(resolved.matchedRouteName()).isNull();
            assertThat(resolved.displayName()).isEqualTo("入口日志");
        }

        @Test
        @DisplayName("条件成立时走路由模板，菜单名带路由后缀")
        void conditionTrue_usesRouteTemplate() {
            JumpOption option = entryLogOption(List.of(
                    new JumpRoute("ClickHouse", "${appId} in (\"100017451\",\"100017452\")", CK_TEMPLATE, true)));

            ResolvedJump resolved = option.resolve(vars("100017452"));

            assertThat(resolved.urlTemplate()).isEqualTo(CK_TEMPLATE);
            assertThat(resolved.routeMatched()).isTrue();
            assertThat(resolved.displayName()).isEqualTo("入口日志 · ClickHouse");
        }

        @Test
        @DisplayName("所有条件都不成立时走兜底模板")
        void noConditionTrue_usesFallback() {
            JumpOption option = entryLogOption(List.of(
                    new JumpRoute("ClickHouse", "${appId} in ('100017451')", CK_TEMPLATE, true)));

            assertThat(option.resolve(vars("100009")).urlTemplate()).isEqualTo(CLOG_TEMPLATE);
        }

        @Test
        @DisplayName("多条路由各自对应一个链接，按顺序第一条成立的生效")
        void multipleRoutes_firstTrueWins() {
            JumpOption option = entryLogOption(List.of(
                    new JumpRoute("ClickHouse", "${appId} in ('100017451', '100017452')", CK_TEMPLATE, true),
                    new JumpRoute("ClickHouse-B", "${appId} startsWith '1000174'", CK_B_TEMPLATE, true)));

            assertThat(option.resolve(vars("100017451")).matchedRouteName()).isEqualTo("ClickHouse");
            assertThat(option.resolve(vars("100017459")).matchedRouteName()).isEqualTo("ClickHouse-B");
            assertThat(option.resolve(vars("200000001")).routeMatched()).isFalse();
        }

        @Test
        @DisplayName("条件可以使用 methodName")
        void condition_canUseMethodName() {
            JumpOption option = entryLogOption(List.of(
                    new JumpRoute("CK-查询类", "${methodName} startsWith 'get'", CK_TEMPLATE, true)));

            assertThat(option.resolve(vars("1")).urlTemplate()).isEqualTo(CK_TEMPLATE);
        }

        @Test
        @DisplayName("跳过停用的路由")
        void disabledRoute_isSkipped() {
            JumpOption option = entryLogOption(List.of(
                    new JumpRoute("ClickHouse", "${appId} == '100001'", CK_TEMPLATE, false),
                    new JumpRoute("ClickHouse-B", "${appId} == '100001'", CK_B_TEMPLATE, true)));

            assertThat(option.resolve(vars("100001")).urlTemplate()).isEqualTo(CK_B_TEMPLATE);
        }

        @Test
        @DisplayName("条件非法或求值出错的路由被跳过，继续匹配后续路由")
        void brokenCondition_isSkipped() {
            JumpOption option = entryLogOption(List.of(
                    new JumpRoute("语法错误", "${appId} = '100001'", CK_TEMPLATE, true),
                    new JumpRoute("求值出错", "${methodName} > 1", CK_TEMPLATE, true),
                    new JumpRoute("ClickHouse-B", "${appId} == '100001'", CK_B_TEMPLATE, true)));

            assertThat(option.resolve(vars("100001")).urlTemplate()).isEqualTo(CK_B_TEMPLATE);
        }

        @Test
        @DisplayName("出错的路由会通知调用方，便于在界面上提示")
        void brokenCondition_reportedToCallback() {
            JumpOption option = entryLogOption(List.of(
                    new JumpRoute("号段", "${appId} > 100", CK_TEMPLATE, true),
                    new JumpRoute("空条件", null, CK_TEMPLATE, true)));
            List<String> failedRoutes = new ArrayList<>();

            ResolvedJump resolved = option.resolve(vars("abc"), (route, error) -> failedRoutes.add(route.getName()));

            assertThat(resolved.routeMatched()).isFalse();
            assertThat(failedRoutes).containsExactly("号段", "空条件");
        }

        @Test
        @DisplayName("模板为空的路由被跳过")
        void blankRouteTemplate_isSkipped() {
            JumpOption option = entryLogOption(List.of(
                    new JumpRoute("Broken", "${appId} == '100001'", "  ", true)));

            assertThat(option.resolve(vars("100001")).routeMatched()).isFalse();
        }

        @Test
        @DisplayName("命中名称为 null 的路由仍视为命中，菜单不加后缀")
        void nullRouteName_stillMatched() {
            JumpOption option = entryLogOption(List.of(
                    new JumpRoute(null, "${appId} == '100001'", CK_TEMPLATE, true)));

            ResolvedJump resolved = option.resolve(vars("100001"));

            assertThat(resolved.routeMatched()).isTrue();
            assertThat(resolved.displayName()).isEqualTo("入口日志");
        }
    }

    @Nested
    @DisplayName("copy / equals")
    class CopyAndEquals {

        @Test
        @DisplayName("copy 深拷贝路由，修改副本不影响原对象")
        void copy_isDeep() {
            JumpOption original = entryLogOption(List.of(
                    new JumpRoute("ClickHouse", "${appId} == '1'", CK_TEMPLATE, true)));

            JumpOption copy = original.copy();
            copy.getRoutes().get(0).setCondition("${appId} == '2'");
            copy.getRoutes().add(new JumpRoute("B", "true", CK_B_TEMPLATE, true));

            assertThat(original.getRoutes()).hasSize(1);
            assertThat(original.getRoutes().get(0).getCondition()).isEqualTo("${appId} == '1'");
        }

        @Test
        @DisplayName("equals 包含路由列表，否则设置页无法感知路由修改")
        void equals_includesRoutes() {
            JumpOption withRoute = entryLogOption(List.of(
                    new JumpRoute("ClickHouse", "${appId} == '1'", CK_TEMPLATE, true)));

            assertThat(withRoute).isNotEqualTo(entryLogOption(List.of()));
            assertThat(withRoute).isEqualTo(withRoute.copy());
            assertThat(withRoute.hashCode()).isEqualTo(withRoute.copy().hashCode());
        }

        @Test
        @DisplayName("setRoutes(null) 置为空列表")
        void setRoutes_null_becomesEmpty() {
            JumpOption option = new JumpOption();

            option.setRoutes(null);

            assertThat(option.getRoutes()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("持久化兼容")
    class Persistence {

        @Test
        @DisplayName("带路由的跳转项按平台的跳过默认值方式序列化后能完整还原")
        void serializeAndDeserialize_keepsRoutes() {
            SoaHelperSettings settings = new SoaHelperSettings();
            settings.setJumpOptions(new ArrayList<>(List.of(
                    entryLogOption(List.of(
                            new JumpRoute("ClickHouse", "${appId} in (\"100017451\", '100017452') && x < 1",
                                    CK_TEMPLATE, true),
                            new JumpRoute("ClickHouse-B", "${appId} startsWith '1000'", CK_B_TEMPLATE, false))),
                    new JumpOption("Bat", CK_B_TEMPLATE, false))));

            Element element = serializeLikePlatform(settings);
            SoaHelperSettings restored = XmlSerializer.deserialize(element, SoaHelperSettings.class);

            assertThat(restored.getJumpOptions()).isEqualTo(settings.getJumpOptions());
        }

        @Test
        @DisplayName("没有路由的跳转项序列化结果不含 routes，与旧版本配置保持一致")
        void optionWithoutRoutes_doesNotWriteRoutesElement() {
            SoaHelperSettings settings = new SoaHelperSettings();
            settings.setJumpOptions(new ArrayList<>(List.of(new JumpOption("Clog", CLOG_TEMPLATE))));

            String xml = JDOMUtil.write(serializeLikePlatform(settings));

            assertThat(xml).contains("Clog").doesNotContain("routes");
        }

        @Test
        @DisplayName("旧版本配置（没有 routes）读取后路由为空，行为与之前一致")
        void legacyXmlWithoutRoutes_loadsWithEmptyRoutes() throws Exception {
            String legacyXml = """
                    <SoaHelperSettings>
                      <option name="jumpOptions">
                        <list>
                          <JumpOption>
                            <option name="enabled" value="true" />
                            <option name="name" value="Clog" />
                            <option name="urlTemplate" value="https://clog.example.com?app=${appId}" />
                          </JumpOption>
                        </list>
                      </option>
                    </SoaHelperSettings>
                    """;
            Element element = JDOMUtil.load(legacyXml);

            SoaHelperSettings restored = XmlSerializer.deserialize(element, SoaHelperSettings.class);

            assertThat(restored.getJumpOptions()).hasSize(1);
            JumpOption option = restored.getJumpOptions().get(0);
            assertThat(option.getName()).isEqualTo("Clog");
            assertThat(option.getRoutes()).isEmpty();
            assertThat(option.resolve(vars("100001")).urlTemplate()).isEqualTo("https://clog.example.com?app=${appId}");
        }

        private Element serializeLikePlatform(SoaHelperSettings settings) {
            return XmlSerializer.serialize(settings, new SkipDefaultsSerializationFilter());
        }
    }
}
