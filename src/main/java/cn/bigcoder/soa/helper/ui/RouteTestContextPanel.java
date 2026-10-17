package cn.bigcoder.soa.helper.ui;

import cn.bigcoder.soa.helper.settings.JumpRoute;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Map;

/**
 * 路由测试输入：填写 appId / methodName，用于实时验证路由条件和预览 URL
 */
final class RouteTestContextPanel extends JPanel {

    private final JTextField appIdField;
    private final JTextField methodNameField;

    RouteTestContextPanel(Runnable onChange) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));

        appIdField = new JTextField(JumpTemplatePreview.SAMPLE_APP_ID, 12);
        methodNameField = new JTextField(JumpTemplatePreview.SAMPLE_METHOD_NAME, 14);

        add(new JLabel("appId "));
        add(appIdField);
        add(Box.createHorizontalStrut(12));
        add(new JLabel("methodName "));
        add(methodNameField);

        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChange.run();
            }
        };
        appIdField.getDocument().addDocumentListener(listener);
        methodNameField.getDocument().addDocumentListener(listener);
    }

    /**
     * 当前测试变量，留空时使用示例值
     */
    Map<String, String> variables() {
        return JumpRoute.conditionVariables(
                valueOrDefault(appIdField, JumpTemplatePreview.SAMPLE_APP_ID),
                valueOrDefault(methodNameField, JumpTemplatePreview.SAMPLE_METHOD_NAME));
    }

    private static String valueOrDefault(JTextField field, String defaultValue) {
        String text = field.getText().trim();
        return text.isEmpty() ? defaultValue : text;
    }
}
