package cn.bigcoder.soa.helper.ui;

import cn.bigcoder.soa.helper.settings.JumpRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JumpRoutesTableModelTest {

    private static final String TEMPLATE = "https://ck.example.com?app=${appId}";

    private JumpRoutesTableModel model;

    @BeforeEach
    void setUp() {
        model = new JumpRoutesTableModel();
        model.setRoutes(List.of(
                new JumpRoute("A", "${appId} == '1'", TEMPLATE, true),
                new JumpRoute("B", "${appId} == '2'", TEMPLATE, true),
                new JumpRoute("C", "${appId} == '3'", TEMPLATE, true)));
    }

    @Test
    @DisplayName("setRoutes 拷贝传入的路由，外部修改不影响表格")
    void setRoutes_copiesInput() {
        List<JumpRoute> source = new ArrayList<>(List.of(new JumpRoute("A", "${appId} == '1'", TEMPLATE, true)));
        model.setRoutes(source);

        source.get(0).setCondition("${appId} == '9'");

        assertThat(model.getRouteAt(0).getCondition()).isEqualTo("${appId} == '1'");
    }

    @Test
    @DisplayName("getRoutes 返回副本，修改副本不影响表格")
    void getRoutes_returnsCopies() {
        List<JumpRoute> routes = model.getRoutes();

        routes.get(0).setName("changed");
        routes.remove(1);

        assertThat(model.getRowCount()).isEqualTo(3);
        assertThat(model.getRouteAt(0).getName()).isEqualTo("A");
    }

    @Test
    @DisplayName("上移/下移调整路由顺序，越界时不变")
    void moveUpAndDown_reorderRoutes() {
        model.moveUp(2);
        assertThat(names()).containsExactly("A", "C", "B");

        model.moveDown(0);
        assertThat(names()).containsExactly("C", "A", "B");

        model.moveUp(0);
        model.moveDown(2);
        assertThat(names()).containsExactly("C", "A", "B");
    }

    @Test
    @DisplayName("勾选启用列会切换路由启用状态")
    void setValueAt_enabledColumn_togglesRoute() {
        model.setValueAt(false, 1, 0);

        assertThat(model.getRouteAt(1).isEnabled()).isFalse();
        assertThat(model.getValueAt(1, 0)).isEqualTo(false);
    }

    @Test
    @DisplayName("条件列把多行表达式压成一行显示")
    void conditionColumn_showsSingleLine() {
        model.addRoute(new JumpRoute("D", "${appId} in (\n  '1',\n  '2'\n)", TEMPLATE, true));

        assertThat(model.getValueAt(3, 2)).isEqualTo("${appId} in ( '1', '2' )");
    }

    private List<String> names() {
        return model.getRoutes().stream().map(JumpRoute::getName).toList();
    }
}
