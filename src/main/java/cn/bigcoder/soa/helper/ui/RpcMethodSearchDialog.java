package cn.bigcoder.soa.helper.ui;

import cn.bigcoder.soa.helper.search.IndexLoadHook;
import cn.bigcoder.soa.helper.search.RpcMethodCache;
import cn.bigcoder.soa.helper.search.RpcMethodHistoryInfo;
import cn.bigcoder.soa.helper.search.RpcMethodHistoryManager;
import cn.bigcoder.soa.helper.search.RpcMethodInfo;
import cn.bigcoder.soa.helper.util.KeywordUtil;
import com.intellij.icons.AllIcons.Nodes;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RpcMethodSearchDialog extends DialogWrapper {

    private final RpcMethodCache cache;
    private JTextField searchField;
    private JBList<RpcMethodInfo> resultList;
    private JButton refreshButton;
    private RpcMethodInfo selectedMethod;
    /**
     * 项目索引是否加载完成
     */
    private boolean projectIndexReady;
    private JLabel statusLabel;
    private JPanel centerPanel;
    private String loadIndexHookId;
    private final RpcMethodHistoryManager historyManager;

    private static final String PLUGIN_SEPARATOR = "%&PLUGIN_SEPARATOR&%";
    /**
     * 默认宽度，可按需调整
     */
    private static final int DEFAULT_WIDTH = 700;
    /**
     * 默认高度，可按需调整
     */
    private static final int DEFAULT_HEIGHT = 600;
    /**
     * 默认字体大小
     */
    private static final int DEFAULT_FONT_SIZE = 15;

    public RpcMethodSearchDialog(Project project, RpcMethodCache cache) {
        super(project, false);
        this.cache = cache;
        this.projectIndexReady = cache.isProjectIndexReady();
        historyManager = RpcMethodHistoryManager.getInstance(project);
        setTitle("SOA RPC Method Search");
        init();
        this.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        this.loadIndexHookId = this.cache.registerIndexLoadHook(new IndexLoadHook() {
            @Override
            public void afterProjectIndexLoad() {
                projectIndexReady = true;
                refreshIndexLoading();
            }

            @Override
            public void beforeProjectIndexLoad() {
                // 项目索引开始加载
                projectIndexReady = false;
                // 更新索引加载状态
                refreshIndexLoading();
            }


            @Override
            public void beforeSoaMethodLoad() {
                searchField.setEnabled(false);
                refreshButton.setEnabled(false);
                setStatusText("正在加载soa服务方法索引...");
                initResultList();
            }

            @Override
            public void afterSoaMethodLoad() {
                searchField.setEnabled(true);
                refreshButton.setEnabled(true);
                setStatusText("");
                initResultList();
            }
        });
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(JBUI.Borders.empty(8));

        // 搜索框容器
        JPanel searchPanel = new JPanel(new BorderLayout());

        searchField = new JTextField();
        searchField.setFont(searchField.getFont().deriveFont(Font.PLAIN, DEFAULT_FONT_SIZE));
        searchField.setBorder(null);
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                // 仅在输入框有文本时更新结果
                if (projectIndexReady) {
                    updateResults();
                }

                if (e.getKeyCode() == KeyEvent.VK_ENTER && !resultList.isEmpty()) {
                    resultList.setSelectedIndex(0);
                    doOKAction();
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (!resultList.isEmpty()) {
                        resultList.requestFocusInWindow();
                        resultList.setSelectedIndex(0);
                        e.consume();
                    }
                }
            }
        });
        searchPanel.add(searchField, BorderLayout.CENTER);

        // 添加刷新按钮
        refreshButton = new JButton(AllIcons.Actions.Refresh);
        refreshButton.setToolTipText("重新构建索引");
        // 移除按钮边框和外边距
        refreshButton.setBorder(null);
        refreshButton.setBorderPainted(false);
        refreshButton.setContentAreaFilled(false);
        refreshButton.setMargin(JBUI.emptyInsets());
        // 设置刷新按钮的固定尺寸，使其高度与搜索框一致
        refreshButton.setPreferredSize(new Dimension(30, searchField.getPreferredSize().height));
        refreshButton.addActionListener(e -> {
            cache.asyncScanRpcMethods();
        });
        searchPanel.add(refreshButton, BorderLayout.EAST);

        centerPanel.add(searchPanel, BorderLayout.NORTH);

        // 结果列表
        resultList = new JBList<>();
        resultList.setFixedCellHeight(30);
        resultList.setCellRenderer(new RpcMethodListCellRenderer(this));
        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectedMethod = resultList.getSelectedValue();
            }
        });

        resultList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                int index = resultList.locationToIndex(evt.getPoint());
                // 检查是否为分隔符
                if (index != -1) {
                    RpcMethodInfo element = resultList.getModel().getElementAt(index);
                    if (PLUGIN_SEPARATOR.equals(element.methodName())) {
                        evt.consume(); // 消耗事件，阻止后续处理
                        return;
                    }
                }
                if (evt.getClickCount() == 2) {
                    doOKAction();
                }
            }
        });

        resultList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();
                // 优先处理上下方向键
                if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN) {
                    int currentIndex = resultList.getSelectedIndex();
                    int direction = keyCode == KeyEvent.VK_DOWN ? 1 : -1;
                    int newIndex = currentIndex + direction;
                    // 跳过所有连续的分隔符
                    while (newIndex >= 0 && newIndex < resultList.getModel().getSize()) {
                        RpcMethodInfo element = resultList.getModel().getElementAt(newIndex);
                        if (element == null || element.methodName() == null || !PLUGIN_SEPARATOR.equals(
                                element.methodName())) {
                            break;
                        }
                        newIndex += direction;
                    }
                    if (newIndex >= 0 && newIndex < resultList.getModel().getSize()) {
                        resultList.setSelectedIndex(newIndex);
                    }
                    e.consume();
                    return;
                }
                // 仅处理非方向键、非左右键的情况
                if (keyCode != KeyEvent.VK_LEFT && keyCode != KeyEvent.VK_RIGHT) {
                    if (keyCode == KeyEvent.VK_ENTER) {
                        if (!resultList.isEmpty()) {
                            doOKAction();
                        }
                        e.consume();
                        return;
                    }
                    searchField.requestFocusInWindow(); // 仅非方向键时转移焦点
                    if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                        String text = searchField.getText();
                        if (!text.isEmpty()) {
                            searchField.setText(text.substring(0, text.length() - 1));
                        }
                        // 仅在输入框有文本时更新结果
                        if (projectIndexReady) {
                            updateResults();
                        }
                        e.consume();
                    } else if (Character.isLetterOrDigit(e.getKeyChar()) || Character.isWhitespace(e.getKeyChar())) {
                        searchField.setText(searchField.getText() + e.getKeyChar());
                        // 仅在输入框有文本时更新结果
                        if (projectIndexReady) {
                            updateResults();
                        }
                    }
                }
            }
        });

        // 滚动面板
        JBScrollPane scrollPane = new JBScrollPane(resultList);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // 状态面板
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, DEFAULT_FONT_SIZE - 2));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        // 主内容面板
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        contentPanel.add(statusPanel, BorderLayout.SOUTH);

        centerPanel.add(contentPanel, BorderLayout.CENTER);

        // 确保搜索框获取焦点
        searchField.requestFocusInWindow();

        // 启动索引加载
        refreshIndexLoading();

        return centerPanel;
    }

    /**
     * 启动索引加载过程
     */
    private void refreshIndexLoading() {
        // 如果索引尚未准备好，启动加载
        if (!projectIndexReady) {
            setStatusText("项目索引构建中，请稍后再试....");
            searchField.setEnabled(false);
        } else {
            setStatusText("");
            searchField.setEnabled(true);
            initResultList();
        }
    }

    /**
     * 初始化结果列表
     * 历史记录优先展示，然后显示剩余方法
     */
    private void initResultList() {
        // 索引加载完成后显示历史记录
        List<RpcMethodHistoryInfo> history = historyManager.getHistories();
        if (!history.isEmpty() && searchField.getText().isEmpty()) {
            List<RpcMethodInfo> rpcMethodInfos = cache.searchAll();
            if (CollectionUtils.isEmpty(rpcMethodInfos)) {
                resultList.setListData(new RpcMethodInfo[0]);
                return;
            }
            Map<String, RpcMethodInfo> cacheMap = rpcMethodInfos.stream()
                    .collect(Collectors.toMap(e -> e.className() + "#" + e.methodName(), Function.identity(),
                            (o1, o2) -> o1));

            List<RpcMethodInfo> historyList = history.stream()
                    .map(e -> cacheMap.get(e.getClassName() + "#" + e.getMethodName()))
                    .filter(Objects::nonNull)
                    .toList();

            // 计算历史方法的key集合
            Set<String> historyKeys = historyList.stream()
                    .map(e -> e.className() + "#" + e.methodName())
                    .collect(Collectors.toSet());

            // 计算剩余方法列表（排除历史方法）
            List<RpcMethodInfo> remainingMethods = rpcMethodInfos.stream()
                    .filter(e -> !historyKeys.contains(e.className() + "#" + e.methodName()))
                    .toList();

            // 合并历史方法和剩余方法
            List<RpcMethodInfo> combinedList = new ArrayList<>();
            combinedList.addAll(historyList);
            // 添加空方法，用于分隔历史方法和剩余方法
            combinedList.add(new RpcMethodInfo(PLUGIN_SEPARATOR, null, null, 0));
            combinedList.addAll(remainingMethods);

            resultList.setListData(combinedList.toArray(new RpcMethodInfo[0]));
        }
    }

    /**
     * 更新状态文本
     */
    private void setStatusText(String text) {
        statusLabel.setText(text);
    }

    private void updateResults() {
        String query = searchField.getText().toLowerCase();
        if (query.isEmpty()) {
            // 搜索词清空后，重新初始化结果列表
            initResultList();
        } else {
            List<RpcMethodInfo> results = cache.search(query);
            resultList.setListData(results.toArray(new RpcMethodInfo[0]));
        }
    }

    public RpcMethodInfo getSelectedMethod() {
        return selectedMethod;
    }

    @Override
    protected void doOKAction() {
        selectedMethod = resultList.getSelectedValue();
        if (selectedMethod != null) {
            RpcMethodHistoryInfo rpcMethodHistoryInfo = new RpcMethodHistoryInfo(selectedMethod.methodName(),
                    selectedMethod.className());
            historyManager.addToHistory(rpcMethodHistoryInfo);
        }
        super.doOKAction();
        close(OK_EXIT_CODE);
    }

    @Override
    protected JComponent createSouthPanel() {
        return null;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[0];
    }

    private static class RpcMethodListCellRenderer extends DefaultListCellRenderer {

        private final RpcMethodSearchDialog dialog;

        public RpcMethodListCellRenderer(RpcMethodSearchDialog dialog) {
            this.dialog = dialog;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof RpcMethodInfo method) {
                String query = dialog.searchField.getText().toLowerCase();
                String methodName = method.methodName();
                String className = method.className();
                // 设置左右内边距（例如左右各10像素）
                setBorder(JBUI.Borders.empty(0, 8)); // 上下0，左右10

                // 检查是否为分隔符
                if (method.methodName() != null && method.methodName().equals(PLUGIN_SEPARATOR)) {
                    // 设置分隔符文案
                    setText("──────────────────── 以下是更多方法 ──────────────────────");
                    setPreferredSize(new Dimension(list.getWidth(), 20));
                    setBackground(list.getBackground());
                    // 设置不可选中和获取焦点
                    setEnabled(false);
                    setFocusable(false);
                    setRequestFocusEnabled(false);
                    return this;
                }

                String highlightedMethodName = KeywordUtil.highlightMatches(methodName, query,
                        "<font style='background-color: #BA9752;color: black;'>", "</font>");

                setText("<html><div style='white-space: nowrap; display: inline-block'><span>" + highlightedMethodName
                        + "</span> <font color='gray'>of " + className
                        + "</font></div></html>");
                // 设置 IDEA 官方类图标
                setIcon(Nodes.Method);
            }
            setFont(getFont().deriveFont(Font.PLAIN, DEFAULT_FONT_SIZE));
            return this;
        }
    }

    @Override
    public void dispose() {
        // 调用父类的dispose()方法，确保基础资源被正确释放
        super.dispose();
        // 删除钩子，防止内存泄露
        cache.removeIndexLoadHook(loadIndexHookId);
    }
}