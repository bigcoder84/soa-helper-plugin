package cn.bigcoder.soa.helper.ui;

import cn.bigcoder.soa.helper.expression.RouteExpression;
import cn.bigcoder.soa.helper.expression.RouteExpressionException;
import cn.bigcoder.soa.helper.settings.JumpRoute;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Optional;

/**
 * 路由编辑对话框：名称 + 条件表达式 + URL 模板，右侧展示表达式语法
 */
class JumpRouteDialog extends DialogWrapper {

    private static final int HELP_WIDTH_PX = 400;
    private static final String CONDITION_PLACEHOLDER_HINT = "请输入条件表达式，例如 ${appId} in (\"100017451\")";
    private static final Color OK_COLOR = new JBColor(new Color(0, 128, 0), new Color(98, 150, 85));
    private static final Color WARNING_COLOR = new JBColor(new Color(200, 130, 0), new Color(204, 153, 51));
    private static final Color PREVIEW_BACKGROUND = new JBColor(new Color(245, 245, 245), new Color(60, 63, 65));

    private final JTextField nameField;
    private final JTextArea conditionArea;
    private final JLabel conditionStatusLabel;
    private final JTextArea urlTemplateArea;
    private final RouteTestContextPanel testContextPanel;
    private final JTextArea previewArea;
    private final JCheckBox enabledCheckBox;

    JumpRouteDialog(Component parent, @Nullable JumpRoute route) {
        super(parent, true);

        nameField = new JTextField(30);
        nameField.setToolTipText("命中时显示在菜单后缀，如：入口日志 · ClickHouse");

        conditionArea = new JTextArea(4, 50);
        conditionArea.setLineWrap(true);
        conditionArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, conditionArea.getFont().getSize()));
        conditionStatusLabel = new JLabel(" ");

        urlTemplateArea = new JTextArea(5, 50);
        urlTemplateArea.setLineWrap(true);
        urlTemplateArea.setWrapStyleWord(false);

        testContextPanel = new RouteTestContextPanel(this::refresh);

        previewArea = new JTextArea(3, 50);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setEditable(false);
        previewArea.setBackground(PREVIEW_BACKGROUND);

        enabledCheckBox = new JCheckBox("启用该路由", true);

        if (route != null) {
            nameField.setText(route.getName());
            conditionArea.setText(route.getCondition());
            urlTemplateArea.setText(route.getUrlTemplate());
            enabledCheckBox.setSelected(route.isEnabled());
            setTitle("编辑路由");
        } else {
            setTitle("添加路由");
        }

        DocumentListener refreshListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        };
        conditionArea.getDocument().addDocumentListener(refreshListener);
        urlTemplateArea.getDocument().addDocumentListener(refreshListener);

        init();
        refresh();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(12, 0));
        root.add(createFormPanel(), BorderLayout.CENTER);
        root.add(createHelpPanel(), BorderLayout.EAST);
        return root;
    }

    private JPanel createFormPanel() {
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

        // 条件
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("条件："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.weighty = 0.3;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(conditionArea), gbc);

        gbc.gridy = 2;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(conditionStatusLabel, gbc);

        // URL 模板
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        panel.add(new JLabel("URL 模板："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.weighty = 0.4;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(urlTemplateArea), gbc);

        // 测试值
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel("测试值："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(testContextPanel, gbc);

        // 预览
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        panel.add(new JLabel("预览："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.weighty = 0.3;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(previewArea), gbc);

        // 启用
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(enabledCheckBox, gbc);

        return panel;
    }

    private JComponent createHelpPanel() {
        JLabel helpLabel = new JLabel(RouteExpressionHelp.standaloneHtml(HELP_WIDTH_PX));
        helpLabel.setVerticalAlignment(SwingConstants.TOP);
        helpLabel.setBorder(JBUI.Borders.empty(4));
        JBScrollPane scrollPane = new JBScrollPane(helpLabel);
        scrollPane.setBorder(BorderFactory.createTitledBorder("条件表达式语法"));
        scrollPane.setPreferredSize(new Dimension(JBUI.scale(HELP_WIDTH_PX + 40), JBUI.scale(420)));
        return scrollPane;
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        if (nameField.getText().isBlank()) {
            return new ValidationInfo("请填写路由名称", nameField);
        }
        Optional<String> conditionError = JumpRoute.validateCondition(conditionArea.getText());
        if (conditionError.isPresent()) {
            return new ValidationInfo("条件有误：" + conditionError.get(), conditionArea);
        }
        if (urlTemplateArea.getText().isBlank()) {
            return new ValidationInfo("请填写 URL 模板", urlTemplateArea);
        }
        return null;
    }

    JumpRoute getJumpRoute() {
        return new JumpRoute(
                nameField.getText().trim(),
                conditionArea.getText().trim(),
                urlTemplateArea.getText().trim(),
                enabledCheckBox.isSelected());
    }

    private void refresh() {
        refreshConditionStatus();
        refreshPreview();
    }

    /**
     * 实时校验条件，并用测试值计算条件结果
     */
    private void refreshConditionStatus() {
        String condition = conditionArea.getText();
        if (condition.isBlank()) {
            showStatus(CONDITION_PLACEHOLDER_HINT, JBColor.GRAY);
            return;
        }
        Optional<String> error = JumpRoute.validateCondition(condition);
        if (error.isPresent()) {
            showStatus("✗ " + error.get(), JBColor.RED);
            return;
        }
        try {
            boolean matched = RouteExpression.parse(condition).evaluate(testContextPanel.variables());
            showStatus("✓ 表达式有效，测试值下条件" + (matched ? "成立" : "不成立"), OK_COLOR);
        } catch (RouteExpressionException e) {
            showStatus("表达式有效，但用测试值计算时出错：" + e.getMessage(), WARNING_COLOR);
        }
    }

    private void refreshPreview() {
        String template = urlTemplateArea.getText();
        if (template.isBlank()) {
            previewArea.setText("（请输入 URL 模板）");
            return;
        }
        try {
            previewArea.setText(JumpTemplatePreview.render(template, testContextPanel.variables()));
        } catch (Exception e) {
            previewArea.setText("解析错误: " + e.getMessage());
        }
    }

    private void showStatus(String text, Color color) {
        conditionStatusLabel.setText(text);
        conditionStatusLabel.setForeground(color);
    }
}
