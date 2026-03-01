package cn.bigcoder.soa.helper.ui;

import cn.bigcoder.soa.helper.settings.JumpOption;
import cn.bigcoder.soa.helper.settings.LogJumpOption;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SOA Helper 设置面板组件
 */
public class SoaHelperSettingsComponent {
    
    private final JPanel mainPanel;
    private final JCheckBox enabledCheckBox;
    private final JumpOptionsTableModel tableModel;
    private final JBTable table;
    private final LogJumpOptionsTableModel logTableModel;
    private final JBTable logTable;
    
    // 扩展字段配置
    private final JCheckBox extendedFieldsCheckBox;
    private final JTextField momBaseUrlField;
    private final JPasswordField momAccessTokenField;
    private final JSpinner momTimeoutSpinner;
    private final JSpinner momCacheTtlSpinner;
    
    public SoaHelperSettingsComponent() {
        // 初始化总开关复选框
        enabledCheckBox = new JCheckBox("启用 SOA 方法跳转功能", true);
        enabledCheckBox.setToolTipText("关闭后将不在 SOA 方法签名处显示跳转图标");
        
        // ===== SOA 方法快速跳转表格 =====
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
        
        // ===== 日志快速跳转表格 =====
        logTableModel = new LogJumpOptionsTableModel();
        logTable = new JBTable(logTableModel);
        
        // 设置列宽
        logTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        logTable.getColumnModel().getColumn(0).setMaxWidth(50);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(250);
        logTable.getColumnModel().getColumn(3).setPreferredWidth(400);
        
        // 添加双击监听器
        logTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = logTable.rowAtPoint(e.getPoint());
                    int col = logTable.columnAtPoint(e.getPoint());
                    if (row >= 0 && col != 0) {
                        editSelectedLogOption();
                    }
                }
            }
        });
        
        ToolbarDecorator logDecorator = ToolbarDecorator.createDecorator(logTable)
            .setAddAction(button -> addLogJumpOption())
            .setRemoveAction(button -> removeSelectedLogOptions())
            .setEditAction(button -> editSelectedLogOption())
            .setMoveUpAction(button -> moveLogUp())
            .setMoveDownAction(button -> moveLogDown());
        
        JPanel logTablePanel = logDecorator.createPanel();
        
        // ===== 扩展字段配置 =====
        extendedFieldsCheckBox = new JCheckBox("启用扩展字段（projectId、momVersion、serviceCode）", false);
        extendedFieldsCheckBox.setToolTipText("启用后可在 URL 模板中使用契约平台提供的扩展变量");
        
        momBaseUrlField = new JTextField(40);
        momBaseUrlField.setToolTipText("契约平台 API 基础地址，如：http://xxx.com");
        
        momAccessTokenField = new JPasswordField(40);
        momAccessTokenField.setToolTipText("契约平台 Access Token");
        
        momTimeoutSpinner = new JSpinner(new SpinnerNumberModel(5000, 1000, 30000, 500));
        momTimeoutSpinner.setToolTipText("API 请求超时时间（毫秒），建议 3000-10000");
        
        momCacheTtlSpinner = new JSpinner(new SpinnerNumberModel(300, 0, 86400, 60));
        momCacheTtlSpinner.setToolTipText("缓存有效时间（秒），设为 0 表示不缓存");
        
        // 联动：开关控制子字段的 enabled 状态
        extendedFieldsCheckBox.addActionListener(e -> updateExtendedFieldsEnabled());
        updateExtendedFieldsEnabled();
        
        // 构建扩展字段面板
        JPanel extFieldsPanel = new JPanel(new GridBagLayout());
        extFieldsPanel.setBorder(BorderFactory.createTitledBorder("扩展字段配置"));
        GridBagConstraints eGbc = new GridBagConstraints();
        eGbc.insets = new Insets(4, 8, 4, 8);
        eGbc.anchor = GridBagConstraints.WEST;
        
        eGbc.gridx = 0; eGbc.gridy = 0; eGbc.gridwidth = 2;
        extFieldsPanel.add(extendedFieldsCheckBox, eGbc);
        
        eGbc.gridx = 0; eGbc.gridy = 1; eGbc.gridwidth = 1; eGbc.weightx = 0;
        extFieldsPanel.add(new JLabel("契约平台地址："), eGbc);
        eGbc.gridx = 1; eGbc.weightx = 1; eGbc.fill = GridBagConstraints.HORIZONTAL;
        extFieldsPanel.add(momBaseUrlField, eGbc);
        
        eGbc.gridx = 0; eGbc.gridy = 2; eGbc.weightx = 0; eGbc.fill = GridBagConstraints.NONE;
        extFieldsPanel.add(new JLabel("Access Token："), eGbc);
        eGbc.gridx = 1; eGbc.weightx = 1; eGbc.fill = GridBagConstraints.HORIZONTAL;
        extFieldsPanel.add(momAccessTokenField, eGbc);
        
        eGbc.gridx = 0; eGbc.gridy = 3; eGbc.weightx = 0; eGbc.fill = GridBagConstraints.NONE;
        extFieldsPanel.add(new JLabel("请求超时(ms)："), eGbc);
        eGbc.gridx = 1; eGbc.weightx = 0; eGbc.fill = GridBagConstraints.NONE;
        extFieldsPanel.add(momTimeoutSpinner, eGbc);
        
        eGbc.gridx = 0; eGbc.gridy = 4; eGbc.weightx = 0;
        extFieldsPanel.add(new JLabel("缓存时间(秒)："), eGbc);
        eGbc.gridx = 1; eGbc.weightx = 0;
        extFieldsPanel.add(momCacheTtlSpinner, eGbc);
        
        // 创建帮助文本
        JLabel helpLabel = new JLabel("<html><body style='width: 800px'>" +
            "<b>URL 模板语法说明：</b><br/>" +
            "变量和函数必须使用 <code>${...}</code> 语法包裹，清晰无歧义。<br/><br/>" +
            
            "<b>基本语法：</b><br/>" +
            "• 变量引用：<code>${变量名}</code><br/>" +
            "• 函数调用：<code>${函数名(参数)}</code><br/>" +
            "• 函数嵌套：<code>${函数1(${函数2(${变量})})}</code><br/>" +
            "• 引号包裹：<code>${函数(\"包含特殊字符()的文本\")}</code><br/><br/>" +
            
            "<b>SOA方法跳转 - 内置变量：</b><br/>" +
            "• <code>appId</code> - 应用ID，从 app.properties 或 app.id 文件读取<br/>" +
            "• <code>methodName</code> - SOA 方法名，如：getUserInfo<br/><br/>" +
            
            "<b>SOA方法跳转 - 扩展变量（需启用扩展字段）：</b><br/>" +
            "• <code>projectId</code> - 契约平台项目ID<br/>" +
            "• <code>momVersion</code> - 契约版本号<br/>" +
            "• <code>serviceCode</code> - 服务代码<br/>" +
            "<i>注：扩展变量在跳转时按需获取，需在设置中配置契约平台地址和Token</i><br/><br/>" +
            
            "<b>日志快速跳转 - 内置变量：</b><br/>" +
            "• <code>appId</code> - 应用ID，从 app.properties 或 app.id 文件读取<br/>" +
            "• <code>params[0]</code>, <code>params[1]</code>, ... - 方法调用的第N个参数值<br/>" +
            "  参数值解析规则：字符串字面量直接提取；变量引用追踪初始值；字符串拼接提取静态前缀；String.format提取格式前缀<br/><br/>" +
            
            "<b>内置函数（共6个）：</b><br/>" +
            "• <code>lower(str)</code> - 转换为小写，例：${lower(${methodName})} → getuserinfo<br/>" +
            "• <code>upper(str)</code> - 转换为大写，例：${upper(${methodName})} → GETUSERINFO<br/>" +
            "• <code>urlEncode(str)</code> - URL 编码，例：${urlEncode(${appId})} → 12345<br/>" +
            "• <code>urlDecode(str)</code> - URL 解码<br/>" +
            "• <code>base64Encode(str)</code> - Base64 编码<br/>" +
            "• <code>base64Decode(str)</code> - Base64 解码<br/><br/>" +
            
            "<b>实用示例：</b><br/>" +
            "1. SOA跳转：<code>https://log.com?app=${appId}&method=${methodName}</code><br/>" +
            "2. 日志跳转：<code>https://log.com/application/${appId}/transaction?filter=${params[0]}</code><br/>" +
            "3. 嵌套函数：<code>${urlEncode(${lower(${methodName})})}</code><br/><br/>" +
            
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
            .addVerticalGap(10)
            .addLabeledComponent("日志快速跳转：", logTablePanel, true)
            .addVerticalGap(10)
            .addComponent(extFieldsPanel)
            .addComponentFillVertically(helpLabel, 0)
            .getPanel();
    }
    
    // ===== SOA 方法跳转表格操作 =====
    
    private void addJumpOption() {
        JumpOptionDialog dialog = new JumpOptionDialog(null);
        if (dialog.showAndGet()) {
            JumpOption option = dialog.getJumpOption();
            tableModel.addOption(option);
        }
    }
    
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
    
    private void removeSelectedOptions() {
        int[] selectedRows = table.getSelectedRows();
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            tableModel.removeOption(selectedRows[i]);
        }
    }
    
    private void moveUp() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow > 0) {
            tableModel.moveUp(selectedRow);
            table.getSelectionModel().setSelectionInterval(selectedRow - 1, selectedRow - 1);
        }
    }
    
    private void moveDown() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < tableModel.getRowCount() - 1) {
            tableModel.moveDown(selectedRow);
            table.getSelectionModel().setSelectionInterval(selectedRow + 1, selectedRow + 1);
        }
    }
    
    // ===== 日志快速跳转表格操作 =====
    
    private void addLogJumpOption() {
        LogJumpOptionDialog dialog = new LogJumpOptionDialog(null);
        if (dialog.showAndGet()) {
            LogJumpOption option = dialog.getLogJumpOption();
            logTableModel.addOption(option);
        }
    }
    
    private void editSelectedLogOption() {
        int selectedRow = logTable.getSelectedRow();
        if (selectedRow >= 0) {
            LogJumpOption option = logTableModel.getOptionAt(selectedRow);
            LogJumpOptionDialog dialog = new LogJumpOptionDialog(option);
            if (dialog.showAndGet()) {
                LogJumpOption editedOption = dialog.getLogJumpOption();
                logTableModel.updateOption(selectedRow, editedOption);
            }
        }
    }
    
    private void removeSelectedLogOptions() {
        int[] selectedRows = logTable.getSelectedRows();
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            logTableModel.removeOption(selectedRows[i]);
        }
    }
    
    private void moveLogUp() {
        int selectedRow = logTable.getSelectedRow();
        if (selectedRow > 0) {
            logTableModel.moveUp(selectedRow);
            logTable.getSelectionModel().setSelectionInterval(selectedRow - 1, selectedRow - 1);
        }
    }
    
    private void moveLogDown() {
        int selectedRow = logTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < logTableModel.getRowCount() - 1) {
            logTableModel.moveDown(selectedRow);
            logTable.getSelectionModel().setSelectionInterval(selectedRow + 1, selectedRow + 1);
        }
    }
    
    /**
     * 根据扩展字段开关状态，启用或禁用子控件
     */
    private void updateExtendedFieldsEnabled() {
        boolean enabled = extendedFieldsCheckBox.isSelected();
        momBaseUrlField.setEnabled(enabled);
        momAccessTokenField.setEnabled(enabled);
        momTimeoutSpinner.setEnabled(enabled);
        momCacheTtlSpinner.setEnabled(enabled);
    }
    
    // ===== 公共方法 =====
    
    public JPanel getPanel() {
        return mainPanel;
    }
    
    public boolean isModified(SoaHelperSettings settings) {
        return enabledCheckBox.isSelected() != settings.isEnabled() 
            || !tableModel.getOptions().equals(settings.getJumpOptions())
            || !logTableModel.getOptions().equals(settings.getLogJumpOptions())
            || extendedFieldsCheckBox.isSelected() != settings.isExtendedFieldsEnabled()
            || !momBaseUrlField.getText().equals(settings.getMomBaseUrl())
            || !new String(momAccessTokenField.getPassword()).equals(settings.getMomAccessToken())
            || (int) momTimeoutSpinner.getValue() != settings.getMomTimeout()
            || (int) momCacheTtlSpinner.getValue() != settings.getMomCacheTtl();
    }
    
    public void apply(SoaHelperSettings settings) {
        settings.setEnabled(enabledCheckBox.isSelected());
        settings.setJumpOptions(new ArrayList<>(tableModel.getOptions()));
        settings.setLogJumpOptions(new ArrayList<>(logTableModel.getOptions()));
        settings.setExtendedFieldsEnabled(extendedFieldsCheckBox.isSelected());
        settings.setMomBaseUrl(momBaseUrlField.getText());
        settings.setMomAccessToken(new String(momAccessTokenField.getPassword()));
        settings.setMomTimeout((int) momTimeoutSpinner.getValue());
        settings.setMomCacheTtl((int) momCacheTtlSpinner.getValue());
    }
    
    public void reset(SoaHelperSettings settings) {
        enabledCheckBox.setSelected(settings.isEnabled());
        tableModel.setOptions(settings.getJumpOptions());
        logTableModel.setOptions(settings.getLogJumpOptions());
        extendedFieldsCheckBox.setSelected(settings.isExtendedFieldsEnabled());
        momBaseUrlField.setText(settings.getMomBaseUrl());
        momAccessTokenField.setText(settings.getMomAccessToken());
        momTimeoutSpinner.setValue(settings.getMomTimeout());
        momCacheTtlSpinner.setValue(settings.getMomCacheTtl());
        updateExtendedFieldsEnabled();
    }
    
    public boolean isExtendedFieldsEnabled() {
        return extendedFieldsCheckBox.isSelected();
    }
    
    public String getMomBaseUrl() {
        return momBaseUrlField.getText();
    }
    
    public String getMomAccessToken() {
        return new String(momAccessTokenField.getPassword());
    }
    
    // ===== SOA 跳转选项表格模型 =====
    
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
            return columnIndex == 0;
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
    
    // ===== 日志跳转选项表格模型 =====
    
    private static class LogJumpOptionsTableModel extends AbstractTableModel {
        private final String[] columnNames = {"启用", "名称", "方法模式", "URL模板"};
        private final List<LogJumpOption> options = new ArrayList<>();
        
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
            return columnIndex == 0;
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            LogJumpOption option = options.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return option.isEnabled();
                case 1:
                    return option.getName();
                case 2:
                    return option.getMethodPatternsShortDisplay();
                case 3:
                    return option.getUrlTemplate();
                default:
                    return null;
            }
        }
        
        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                LogJumpOption option = options.get(rowIndex);
                option.setEnabled((Boolean) aValue);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
        
        public void addOption(LogJumpOption option) {
            options.add(option);
            fireTableRowsInserted(options.size() - 1, options.size() - 1);
        }
        
        public void updateOption(int index, LogJumpOption option) {
            options.set(index, option);
            fireTableRowsUpdated(index, index);
        }
        
        public void removeOption(int index) {
            options.remove(index);
            fireTableRowsDeleted(index, index);
        }
        
        public void moveUp(int index) {
            if (index > 0) {
                LogJumpOption option = options.remove(index);
                options.add(index - 1, option);
                fireTableRowsUpdated(index - 1, index);
            }
        }
        
        public void moveDown(int index) {
            if (index < options.size() - 1) {
                LogJumpOption option = options.remove(index);
                options.add(index + 1, option);
                fireTableRowsUpdated(index, index + 1);
            }
        }
        
        public LogJumpOption getOptionAt(int index) {
            return options.get(index);
        }
        
        public List<LogJumpOption> getOptions() {
            return new ArrayList<>(options);
        }
        
        public void setOptions(List<LogJumpOption> newOptions) {
            options.clear();
            for (LogJumpOption option : newOptions) {
                options.add(option.copy());
            }
            fireTableDataChanged();
        }
    }
    
    // ===== SOA 跳转选项编辑对话框 =====
    
    private static class JumpOptionDialog extends DialogWrapper {
        private final JTextField nameField;
        private final JTextArea urlTemplateArea;
        private final JCheckBox enabledCheckBox;
        private final JTextArea previewArea;
        private final TemplateParser templateParser;
        private JLabel extHintLabel;
        
        public JumpOptionDialog(@Nullable JumpOption option) {
            super(true);
            
            nameField = new JTextField(30);
            urlTemplateArea = new JTextArea(8, 50);
            urlTemplateArea.setLineWrap(true);
            urlTemplateArea.setWrapStyleWord(false);
            enabledCheckBox = new JCheckBox("启用该选项", true);
            
            previewArea = new JTextArea(4, 50);
            previewArea.setLineWrap(true);
            previewArea.setWrapStyleWord(true);
            previewArea.setEditable(false);
            previewArea.setBackground(new Color(245, 245, 245));
            
            templateParser = new TemplateParser();
            
            if (option != null) {
                nameField.setText(option.getName());
                urlTemplateArea.setText(option.getUrlTemplate());
                enabledCheckBox.setSelected(option.isEnabled());
                setTitle("编辑跳转选项");
            } else {
                setTitle("添加跳转选项");
            }
            
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
            
            // 预览
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.weightx = 0;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            JLabel previewLabel = new JLabel("预览（示例值）：");
            previewLabel.setToolTipText("appId=12345, methodName=getUserInfo");
            panel.add(previewLabel, gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 2;
            gbc.weightx = 1;
            gbc.weighty = 0.4;
            gbc.fill = GridBagConstraints.BOTH;
            JScrollPane previewScrollPane = new JScrollPane(previewArea);
            panel.add(previewScrollPane, gbc);
            
            // 扩展变量提示
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.gridwidth = 2;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            extHintLabel = new JLabel("");
            extHintLabel.setFont(extHintLabel.getFont().deriveFont(Font.ITALIC, 11f));
            panel.add(extHintLabel, gbc);
            
            // 启用
            gbc.gridx = 0;
            gbc.gridy = 4;
            gbc.gridwidth = 2;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(enabledCheckBox, gbc);
            
            return panel;
        }
        
        private void updatePreview() {
            String template = urlTemplateArea.getText();
            if (template == null || template.trim().isEmpty()) {
                previewArea.setText("（请输入 URL 模板）");
                return;
            }
            
            try {
                Map<String, String> sampleVars = new HashMap<>();
                sampleVars.put("appId", "12345");
                sampleVars.put("methodName", "getUserInfo");
                sampleVars.put("projectId", "67890");
                sampleVars.put("momVersion", "3");
                sampleVars.put("serviceCode", "sample.service");
                
                String result = templateParser.parse(template, sampleVars);
                previewArea.setText(result);
                
                // 检查模板是否使用了扩展变量
                Set<String> extendedVarNames = Set.of("projectId", "momVersion", "serviceCode");
                boolean usesExtended = extendedVarNames.stream()
                        .anyMatch(v -> template.contains("${" + v + "}"));
                if (extHintLabel != null) {
                    if (usesExtended && !SoaHelperSettings.getInstance().isExtendedFieldsEnabled()) {
                        extHintLabel.setText("该模板使用了扩展变量，请确保在设置中启用扩展字段功能");
                        extHintLabel.setForeground(new Color(200, 130, 0));
                    } else {
                        extHintLabel.setText("");
                    }
                }
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
    
    // ===== 日志跳转选项编辑对话框 =====
    
    private static class LogJumpOptionDialog extends DialogWrapper {
        private static final Pattern PARAMS_PATTERN = Pattern.compile("\\$\\{params\\[(\\d+)]}");
        
        private final JTextField nameField;
        private final JTextArea methodPatternsArea;
        private final JTextArea urlTemplateArea;
        private final JCheckBox enabledCheckBox;
        private final JTextArea previewArea;
        private final TemplateParser templateParser;
        
        public LogJumpOptionDialog(@Nullable LogJumpOption option) {
            super(true);
            
            nameField = new JTextField(30);
            methodPatternsArea = new JTextArea(4, 50);
            methodPatternsArea.setLineWrap(false);
            urlTemplateArea = new JTextArea(6, 50);
            urlTemplateArea.setLineWrap(true);
            urlTemplateArea.setWrapStyleWord(false);
            enabledCheckBox = new JCheckBox("启用该选项", true);
            
            previewArea = new JTextArea(4, 50);
            previewArea.setLineWrap(true);
            previewArea.setWrapStyleWord(true);
            previewArea.setEditable(false);
            previewArea.setBackground(new Color(245, 245, 245));
            
            templateParser = new TemplateParser();
            
            if (option != null) {
                nameField.setText(option.getName());
                methodPatternsArea.setText(option.getMethodPatternsDisplay());
                urlTemplateArea.setText(option.getUrlTemplate());
                enabledCheckBox.setSelected(option.isEnabled());
                setTitle("编辑日志跳转选项");
            } else {
                setTitle("添加日志跳转选项");
            }
            
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
            gbc.anchor = GridBagConstraints.WEST;
            panel.add(new JLabel("名称："), gbc);
            
            gbc.gridx = 1;
            gbc.weightx = 1;
            panel.add(nameField, gbc);
            
            // 方法模式
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            JLabel methodLabel = new JLabel("方法模式：");
            methodLabel.setToolTipText("每行一个，格式：ClassName#MethodName 或 全限定类名#方法名");
            panel.add(methodLabel, gbc);
            
            gbc.gridx = 1;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weighty = 0.3;
            JScrollPane methodScrollPane = new JScrollPane(methodPatternsArea);
            panel.add(methodScrollPane, gbc);
            
            // 方法模式提示
            gbc.gridx = 1;
            gbc.gridy = 2;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            JLabel methodHint = new JLabel("<html><span style='color:gray;font-size:11px'>" +
                "每行一个方法模式，如：CLogger#info 或 cn.bigcoder.CLogger#info" +
                "</span></html>");
            panel.add(methodHint, gbc);
            
            // URL 模板
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            panel.add(new JLabel("URL 模板："), gbc);
            
            gbc.gridx = 1;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weighty = 0.35;
            JScrollPane urlScrollPane = new JScrollPane(urlTemplateArea);
            panel.add(urlScrollPane, gbc);
            
            // URL 模板提示
            gbc.gridx = 1;
            gbc.gridy = 4;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            JLabel urlHint = new JLabel("<html><span style='color:gray;font-size:11px'>" +
                "支持变量：${appId}、${params[0]}、${params[1]}... " +
                "params[N] 为方法调用的第N个参数的解析值" +
                "</span></html>");
            panel.add(urlHint, gbc);
            
            // 预览
            gbc.gridx = 0;
            gbc.gridy = 5;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            JLabel previewLabel = new JLabel("预览（示例值）：");
            previewLabel.setToolTipText("appId=12345, params[0]=sampleLogTitle, params[1]=sampleMessage, params[2]=sampleExtra");
            panel.add(previewLabel, gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 5;
            gbc.weightx = 1;
            gbc.weighty = 0.35;
            gbc.fill = GridBagConstraints.BOTH;
            JScrollPane previewScrollPane = new JScrollPane(previewArea);
            panel.add(previewScrollPane, gbc);
            
            // 启用
            gbc.gridx = 0;
            gbc.gridy = 6;
            gbc.gridwidth = 2;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(enabledCheckBox, gbc);
            
            return panel;
        }
        
        /**
         * 将 ${params[N]} 替换为 ${params_N} 以兼容 TemplateParser 的标识符规则
         */
        private static String normalizeParamsInTemplate(String template) {
            return PARAMS_PATTERN.matcher(template).replaceAll("\\${params_$1}");
        }
        
        private void updatePreview() {
            String template = urlTemplateArea.getText();
            if (template == null || template.trim().isEmpty()) {
                previewArea.setText("（请输入 URL 模板）");
                return;
            }
            
            try {
                // 预处理 ${params[N]} → ${params_N}
                String normalizedTemplate = normalizeParamsInTemplate(template);
                
                Map<String, String> sampleVars = new HashMap<>();
                sampleVars.put("appId", "12345");
                sampleVars.put("params_0", "sampleLogTitle");
                sampleVars.put("params_1", "sampleMessage");
                sampleVars.put("params_2", "sampleExtra");
                
                String result = templateParser.parse(normalizedTemplate, sampleVars);
                previewArea.setText(result);
            } catch (Exception e) {
                previewArea.setText("解析错误: " + e.getMessage());
            }
        }
        
        public LogJumpOption getLogJumpOption() {
            LogJumpOption option = new LogJumpOption();
            option.setName(nameField.getText().trim());
            option.setMethodPatternsFromDisplay(methodPatternsArea.getText());
            option.setUrlTemplate(urlTemplateArea.getText().trim());
            option.setEnabled(enabledCheckBox.isSelected());
            return option;
        }
    }
}
