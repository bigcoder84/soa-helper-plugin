package cn.bigcoder.soa.helper.ui;

import cn.bigcoder.soa.helper.settings.JumpOption;
import cn.bigcoder.soa.helper.settings.JumpRoute;
import cn.bigcoder.soa.helper.settings.ResolvedJump;
import cn.bigcoder.soa.helper.settings.SoaHelperSettings;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SOA 跳转选项编辑对话框
 * 兜底 URL 模板 + 路由列表（按顺序匹配，第一条条件成立的路由生效）
 */
class JumpOptionDialog extends DialogWrapper {

    private static final String ROUTE_HINT =
            "按顺序匹配，第一条条件成立的路由生效；都不成立时使用兜底 URL 模板。双击编辑，编辑框右侧有表达式语法说明";
    private static final Color WARNING_COLOR = new JBColor(new Color(200, 130, 0), new Color(204, 153, 51));
    private static final Color PREVIEW_BACKGROUND = new JBColor(new Color(245, 245, 245), new Color(60, 63, 65));

    private final JTextField nameField;
    private final JTextArea urlTemplateArea;
    private final JumpRoutesTableModel routesTableModel;
    private final JBTable routesTable;
    private final RouteTestContextPanel testContextPanel;
    private final JLabel routeLabel;
    private final JLabel routeErrorLabel;
    private final JTextArea previewArea;
    private final JLabel extHintLabel;
    private final JCheckBox enabledCheckBox;

    JumpOptionDialog(@Nullable JumpOption option) {
        super(true);

        nameField = new JTextField(30);
        urlTemplateArea = new JTextArea(6, 50);
        urlTemplateArea.setLineWrap(true);
        urlTemplateArea.setWrapStyleWord(false);
        enabledCheckBox = new JCheckBox("启用该选项", true);

        routesTableModel = new JumpRoutesTableModel();
        routesTable = new JBTable(routesTableModel);
        routesTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        routesTable.getColumnModel().getColumn(0).setMaxWidth(50);
        routesTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        routesTable.getColumnModel().getColumn(2).setPreferredWidth(260);
        routesTable.getColumnModel().getColumn(3).setPreferredWidth(260);

        testContextPanel = new RouteTestContextPanel(this::updatePreview);
        routeLabel = new JLabel();
        routeErrorLabel = new JLabel();
        routeErrorLabel.setForeground(WARNING_COLOR);

        previewArea = new JTextArea(4, 50);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setEditable(false);
        previewArea.setBackground(PREVIEW_BACKGROUND);

        extHintLabel = new JLabel("");
        extHintLabel.setFont(extHintLabel.getFont().deriveFont(Font.ITALIC, 11f));

        if (option != null) {
            nameField.setText(option.getName());
            urlTemplateArea.setText(option.getUrlTemplate());
            enabledCheckBox.setSelected(option.isEnabled());
            routesTableModel.setRoutes(option.getRoutes());
            setTitle("编辑跳转选项");
        } else {
            setTitle("添加跳转选项");
        }

        urlTemplateArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updatePreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updatePreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updatePreview();
            }
        });
        routesTableModel.addTableModelListener(e -> updatePreview());

        routesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = routesTable.rowAtPoint(e.getPoint());
                    int col = routesTable.columnAtPoint(e.getPoint());
                    // 只有非启用列才触发编辑
                    if (row >= 0 && col != 0) {
                        editSelectedRoute();
                    }
                }
            }
        });

        init();
        updatePreview();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        // 名称
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("名称："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(nameField, gbc);

        // 兜底 URL 模板
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        JLabel templateLabel = new JLabel("兜底 URL 模板：");
        templateLabel.setToolTipText("所有路由条件都不成立时使用");
        panel.add(templateLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.weighty = 0.4;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(urlTemplateArea), gbc);

        // 路由
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel("路由："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.weighty = 0.3;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(createRoutesPanel(), gbc);

        gbc.gridy = 3;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel routeHintLabel = new JLabel(ROUTE_HINT);
        routeHintLabel.setFont(routeHintLabel.getFont().deriveFont(Font.ITALIC, 11f));
        routeHintLabel.setForeground(JBColor.GRAY);
        panel.add(routeHintLabel, gbc);

        // 测试值
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        panel.add(new JLabel("测试值："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(testContextPanel, gbc);

        gbc.gridy = 5;
        JPanel routeResultPanel = new JPanel(new GridLayout(0, 1));
        routeResultPanel.add(routeLabel);
        routeResultPanel.add(routeErrorLabel);
        panel.add(routeResultPanel, gbc);

        // 预览
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.weightx = 0;
        JLabel previewLabel = new JLabel("预览：");
        previewLabel.setToolTipText("使用测试值生成的最终 URL");
        panel.add(previewLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.weighty = 0.3;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(previewArea), gbc);

        // 扩展变量提示
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.gridwidth = 2;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(extHintLabel, gbc);

        // 启用
        gbc.gridy = 8;
        panel.add(enabledCheckBox, gbc);

        return panel;
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        if (nameField.getText().isBlank()) {
            return new ValidationInfo("请填写名称", nameField);
        }
        if (urlTemplateArea.getText().isBlank()) {
            return new ValidationInfo("请填写兜底 URL 模板", urlTemplateArea);
        }
        // 路由在各自的编辑框里已校验；这里兜住手工修改配置文件导致的非法条件
        for (JumpRoute route : routesTableModel.getRoutes()) {
            Optional<String> error = JumpRoute.validateCondition(route.getCondition());
            if (route.isEnabled() && error.isPresent()) {
                return new ValidationInfo("路由「" + route.getName() + "」条件有误：" + error.get(), routesTable);
            }
        }
        return null;
    }

    JumpOption getJumpOption() {
        return new JumpOption(
                nameField.getText().trim(),
                urlTemplateArea.getText().trim(),
                enabledCheckBox.isSelected(),
                routesTableModel.getRoutes());
    }

    private JPanel createRoutesPanel() {
        return ToolbarDecorator.createDecorator(routesTable)
                .setAddAction(button -> addRoute())
                .setRemoveAction(button -> removeSelectedRoutes())
                .setEditAction(button -> editSelectedRoute())
                .setMoveUpAction(button -> moveRouteUp())
                .setMoveDownAction(button -> moveRouteDown())
                .setPreferredSize(new Dimension(680, 130))
                .createPanel();
    }

    // ===== 路由表格操作 =====

    private void addRoute() {
        JumpRouteDialog dialog = new JumpRouteDialog(routesTable, null);
        if (dialog.showAndGet()) {
            routesTableModel.addRoute(dialog.getJumpRoute());
        }
    }

    private void editSelectedRoute() {
        int selectedRow = routesTable.getSelectedRow();
        if (selectedRow >= 0) {
            JumpRouteDialog dialog = new JumpRouteDialog(routesTable, routesTableModel.getRouteAt(selectedRow));
            if (dialog.showAndGet()) {
                routesTableModel.updateRoute(selectedRow, dialog.getJumpRoute());
            }
        }
    }

    private void removeSelectedRoutes() {
        int[] selectedRows = routesTable.getSelectedRows();
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            routesTableModel.removeRoute(selectedRows[i]);
        }
    }

    private void moveRouteUp() {
        int selectedRow = routesTable.getSelectedRow();
        if (selectedRow > 0) {
            routesTableModel.moveUp(selectedRow);
            routesTable.getSelectionModel().setSelectionInterval(selectedRow - 1, selectedRow - 1);
        }
    }

    private void moveRouteDown() {
        int selectedRow = routesTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < routesTableModel.getRowCount() - 1) {
            routesTableModel.moveDown(selectedRow);
            routesTable.getSelectionModel().setSelectionInterval(selectedRow + 1, selectedRow + 1);
        }
    }

    // ===== 预览与提示 =====

    private void updatePreview() {
        JumpOption current = getJumpOption();
        updateExtHint(current);

        List<String> routeErrors = new ArrayList<>();
        ResolvedJump resolved = current.resolve(testContextPanel.variables(),
                (route, error) -> routeErrors.add("路由「" + route.getName() + "」计算出错已跳过：" + error.getMessage()));
        routeErrorLabel.setText(String.join("；", routeErrors));
        routeLabel.setText(resolved.routeMatched()
                ? "→ 命中路由「" + resolved.matchedRouteName() + "」"
                : "→ 没有路由条件成立，使用兜底模板");

        String template = resolved.urlTemplate();
        if (template == null || template.isBlank()) {
            previewArea.setText("（请输入 URL 模板）");
            return;
        }
        try {
            previewArea.setText(JumpTemplatePreview.render(template, testContextPanel.variables()));
        } catch (Exception e) {
            previewArea.setText("解析错误: " + e.getMessage());
        }
    }

    private void updateExtHint(JumpOption current) {
        boolean usesExtended = JumpTemplatePreview.usesExtendedVars(current.getUrlTemplate())
                || current.getRoutes().stream()
                        .anyMatch(route -> JumpTemplatePreview.usesExtendedVars(route.getUrlTemplate()));
        if (usesExtended && !SoaHelperSettings.getInstance().isExtendedFieldsEnabled()) {
            extHintLabel.setText("该模板使用了扩展变量，请确保在设置中启用扩展字段功能");
            extHintLabel.setForeground(WARNING_COLOR);
        } else {
            extHintLabel.setText("");
        }
    }
}
