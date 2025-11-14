package cn.bigcoder.soa.helper.ui;

import cn.bigcoder.soa.helper.settings.JumpOption;
import cn.bigcoder.soa.helper.settings.SoaHelperSettings;
import cn.bigcoder.soa.helper.util.TemplateParser;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SOA Helper 设置面板组件
 */
public class SoaHelperSettingsComponent {
    
    private final JPanel mainPanel;
    private final JCheckBox enabledCheckBox;
    private final JumpOptionsTableModel tableModel;
    private final JBTable table;
    
    public SoaHelperSettingsComponent() {
        // 初始化总开关复选框
        enabledCheckBox = new JCheckBox("启用 SOA 方法跳转功能", true);
        enabledCheckBox.setToolTipText("关闭后将不在 SOA 方法签名处显示跳转图标");
        
        tableModel = new JumpOptionsTableModel();
        table = new JBTable(tableModel);
        
        // 设置列宽
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(500);
        
        // 添加双击监听器
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.rowAtPoint(e.getPoint());
                    int col = table.columnAtPoint(e.getPoint());
                    // 只有非启用列才触发编辑
                    if (row >= 0 && col != 0) {
                        editSelectedOption();
                    }
                }
            }
        });
        
        // 创建工具栏装饰器
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(table)
            .setAddAction(button -> addJumpOption())
            .setRemoveAction(button -> removeSelectedOptions())
            .setEditAction(button -> editSelectedOption())
            .setMoveUpAction(button -> moveUp())
            .setMoveDownAction(button -> moveDown());
        
        JPanel tablePanel = decorator.createPanel();
        
        // 创建帮助文本
        JLabel helpLabel = new JLabel("<html><body style='width: 800px'>" +
            "<b>URL 模板语法说明：</b><br/>" +
            "变量和函数必须使用 <code>${...}</code> 语法包裹，清晰无歧义。<br/><br/>" +
            
            "<b>基本语法：</b><br/>" +
            "• 变量引用：<code>${变量名}</code><br/>" +
            "• 函数调用：<code>${函数名(参数)}</code><br/>" +
            "• 函数嵌套：<code>${函数1(${函数2(${变量})})}</code><br/>" +
            "• 引号包裹：<code>${函数(\"包含特殊字符()的文本\")}</code><br/><br/>" +
            
            "<b>内置变量（共2个）：</b><br/>" +
            "• <code>appId</code> - 应用ID，从 app.properties 或 app.id 文件读取<br/>" +
            "• <code>methodName</code> - SOA 方法名，如：getUserInfo<br/><br/>" +
            
            "<b>内置函数（共6个）：</b><br/>" +
            "• <code>lower(str)</code> - 转换为小写，例：${lower(${methodName})} → getuserinfo<br/>" +
            "• <code>upper(str)</code> - 转换为大写，例：${upper(${methodName})} → GETUSERINFO<br/>" +
            "• <code>urlEncode(str)</code> - URL 编码，例：${urlEncode(${appId})} → 12345<br/>" +
            "• <code>urlDecode(str)</code> - URL 解码<br/>" +
            "• <code>base64Encode(str)</code> - Base64 编码<br/>" +
            "• <code>base64Decode(str)</code> - Base64 解码<br/><br/>" +
            
            "<b>实用示例：</b><br/>" +
            "1. 简单变量：<code>https://log.com?app=${appId}&method=${methodName}</code><br/>" +
            "2. 小写方法名：<code>https://log.com?method=${lower(${methodName})}</code><br/>" +
            "3. 变量边界：<code>https://log.com?key=${methodName}_v1</code><br/>" +
            "4. 复杂JSON：<code>${base64Encode({\"appId\":\"${appId}\",\"method\":\"${methodName}\"})}</code><br/>" +
            "5. 嵌套函数：<code>${urlEncode(${lower(${methodName})})}</code><br/>" +
            "6. 引号消歧：<code>${lower(\"text(with)special\")}</code> - 当参数包含括号等特殊字符时<br/><br/>" +
            
            "<b>注意事项：</b><br/>" +
            "• 所有变量必须用 <code>${}</code> 包裹，不支持 <code>$变量名</code> 语法<br/>" +
            "• 函数参数中的变量也必须用 <code>${}</code> 包裹<br/>" +
            "• 参数包含括号等特殊字符时，建议用引号包裹避免歧义<br/>" +
            "• 支持任意层级的函数嵌套" +
            "</body></html>");
        
        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(enabledCheckBox)
            .addVerticalGap(5)
            .addLabeledComponent("SOA方法快速跳转：", tablePanel, true)
            .addComponentFillVertically(helpLabel, 0)
            .getPanel();
    }
    
    /**
     * 添加新的跳转选项
     */
    private void addJumpOption() {
        JumpOptionDialog dialog = new JumpOptionDialog(null);
        if (dialog.showAndGet()) {
            JumpOption option = dialog.getJumpOption();
            tableModel.addOption(option);
        }
    }
    
    /**
     * 编辑选中的跳转选项
     */
    private void editSelectedOption() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0) {
            JumpOption option = tableModel.getOptionAt(selectedRow);
            JumpOptionDialog dialog = new JumpOptionDialog(option);
            if (dialog.showAndGet()) {
                JumpOption editedOption = dialog.getJumpOption();
                tableModel.updateOption(selectedRow, editedOption);
            }
        }
    }
    
    /**
     * 删除选中的跳转选项
     */
    private void removeSelectedOptions() {
        int[] selectedRows = table.getSelectedRows();
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            tableModel.removeOption(selectedRows[i]);
        }
    }
    
    /**
     * 上移选中项
     */
    private void moveUp() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow > 0) {
            tableModel.moveUp(selectedRow);
            table.getSelectionModel().setSelectionInterval(selectedRow - 1, selectedRow - 1);
        }
    }
    
    /**
     * 下移选中项
     */
    private void moveDown() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < tableModel.getRowCount() - 1) {
            tableModel.moveDown(selectedRow);
            table.getSelectionModel().setSelectionInterval(selectedRow + 1, selectedRow + 1);
        }
    }
    
    public JPanel getPanel() {
        return mainPanel;
    }
    
    public boolean isModified(SoaHelperSettings settings) {
        return enabledCheckBox.isSelected() != settings.isEnabled() 
            || !tableModel.getOptions().equals(settings.getJumpOptions());
    }
    
    public void apply(SoaHelperSettings settings) {
        settings.setEnabled(enabledCheckBox.isSelected());
        settings.setJumpOptions(new ArrayList<>(tableModel.getOptions()));
    }
    
    public void reset(SoaHelperSettings settings) {
        enabledCheckBox.setSelected(settings.isEnabled());
        tableModel.setOptions(settings.getJumpOptions());
    }
    
    /**
     * 跳转选项表格模型
     */
    private static class JumpOptionsTableModel extends AbstractTableModel {
        private final String[] columnNames = {"启用", "名称", "URL模板"};
        private final List<JumpOption> options = new ArrayList<>();
        
        @Override
        public int getRowCount() {
            return options.size();
        }
        
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }
        
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) {
                return Boolean.class;
            }
            return String.class;
        }
        
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0; // 只有启用列可以直接编辑
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            JumpOption option = options.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return option.isEnabled();
                case 1:
                    return option.getName();
                case 2:
                    return option.getUrlTemplate();
                default:
                    return null;
            }
        }
        
        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                JumpOption option = options.get(rowIndex);
                option.setEnabled((Boolean) aValue);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
        
        public void addOption(JumpOption option) {
            options.add(option);
            fireTableRowsInserted(options.size() - 1, options.size() - 1);
        }
        
        public void updateOption(int index, JumpOption option) {
            options.set(index, option);
            fireTableRowsUpdated(index, index);
        }
        
        public void removeOption(int index) {
            options.remove(index);
            fireTableRowsDeleted(index, index);
        }
        
        public void moveUp(int index) {
            if (index > 0) {
                JumpOption option = options.remove(index);
                options.add(index - 1, option);
                fireTableRowsUpdated(index - 1, index);
            }
        }
        
        public void moveDown(int index) {
            if (index < options.size() - 1) {
                JumpOption option = options.remove(index);
                options.add(index + 1, option);
                fireTableRowsUpdated(index, index + 1);
            }
        }
        
        public JumpOption getOptionAt(int index) {
            return options.get(index);
        }
        
        public List<JumpOption> getOptions() {
            return new ArrayList<>(options);
        }
        
        public void setOptions(List<JumpOption> newOptions) {
            options.clear();
            for (JumpOption option : newOptions) {
                options.add(option.copy());
            }
            fireTableDataChanged();
        }
    }
    
    /**
     * 跳转选项编辑对话框
     */
    private static class JumpOptionDialog extends DialogWrapper {
        private final JTextField nameField;
        private final JTextArea urlTemplateArea;
        private final JCheckBox enabledCheckBox;
        private final JTextArea previewArea;
        private final TemplateParser templateParser;
        
        public JumpOptionDialog(@Nullable JumpOption option) {
            super(true);
            
            nameField = new JTextField(30);
            urlTemplateArea = new JTextArea(8, 50);
            urlTemplateArea.setLineWrap(true);
            urlTemplateArea.setWrapStyleWord(false);
            enabledCheckBox = new JCheckBox("启用该选项", true);
            
            // 创建预览区域
            previewArea = new JTextArea(4, 50);
            previewArea.setLineWrap(true);
            previewArea.setWrapStyleWord(true);
            previewArea.setEditable(false);
            previewArea.setBackground(new Color(245, 245, 245));
            
            // 创建模板解析器
            templateParser = new TemplateParser();
            
            if (option != null) {
                nameField.setText(option.getName());
                urlTemplateArea.setText(option.getUrlTemplate());
                enabledCheckBox.setSelected(option.isEnabled());
                setTitle("编辑跳转选项");
            } else {
                setTitle("添加跳转选项");
            }
            
            // 添加文本变化监听器，实时更新预览
            urlTemplateArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    updatePreview();
                }
                
                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    updatePreview();
                }
                
                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    updatePreview();
                }
            });
            
            init();
            
            // 初始化预览
            updatePreview();
        }
        
        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            
            // 名称
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 0;
            panel.add(new JLabel("名称："), gbc);
            
            gbc.gridx = 1;
            gbc.weightx = 1;
            panel.add(nameField, gbc);
            
            // URL 模板
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            panel.add(new JLabel("URL 模板："), gbc);
            
            gbc.gridx = 1;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weighty = 0.6;
            JScrollPane scrollPane = new JScrollPane(urlTemplateArea);
            panel.add(scrollPane, gbc);
            
            // 预览标签
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.weightx = 0;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            JLabel previewLabel = new JLabel("预览（示例值）：");
            previewLabel.setToolTipText("appId=12345, methodName=getUserInfo");
            panel.add(previewLabel, gbc);
            
            // 预览区域
            gbc.gridx = 1;
            gbc.gridy = 2;
            gbc.weightx = 1;
            gbc.weighty = 0.4;
            gbc.fill = GridBagConstraints.BOTH;
            JScrollPane previewScrollPane = new JScrollPane(previewArea);
            panel.add(previewScrollPane, gbc);
            
            // 启用复选框
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.gridwidth = 2;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(enabledCheckBox, gbc);
            
            return panel;
        }
        
        /**
         * 更新 URL 预览
         */
        private void updatePreview() {
            String template = urlTemplateArea.getText();
            if (template == null || template.trim().isEmpty()) {
                previewArea.setText("（请输入 URL 模板）");
                return;
            }
            
            try {
                // 使用示例值进行预览
                java.util.Map<String, String> sampleVars = new java.util.HashMap<>();
                sampleVars.put("appId", "12345");
                sampleVars.put("methodName", "getUserInfo");
                
                String result = templateParser.parse(template, sampleVars);
                previewArea.setText(result);
            } catch (Exception e) {
                previewArea.setText("解析错误: " + e.getMessage());
            }
        }
        
        public JumpOption getJumpOption() {
            return new JumpOption(
                nameField.getText().trim(),
                urlTemplateArea.getText().trim(),
                enabledCheckBox.isSelected()
            );
        }
    }
}

