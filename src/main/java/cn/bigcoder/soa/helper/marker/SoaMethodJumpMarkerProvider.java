package cn.bigcoder.soa.helper.marker;

import cn.bigcoder.soa.helper.settings.JumpOption;
import cn.bigcoder.soa.helper.util.SoaMethodUtil;
import cn.bigcoder.soa.helper.settings.SoaHelperSettings;
import cn.bigcoder.soa.helper.util.AppIdUtil;
import cn.bigcoder.soa.helper.util.NotifyUtil;
import cn.bigcoder.soa.helper.variable.*;
import cn.bigcoder.soa.helper.variable.impl.BasicVariableGroup;
import cn.bigcoder.soa.helper.variable.impl.MomApiVariableGroup;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SOA 方法日志跳转的 LineMarker 提供者
 * 在实现了 @BaijiContract 注解接口的类的方法旁边显示图标
 */
public class SoaMethodJumpMarkerProvider implements LineMarkerProvider {

    private static final Logger LOG = Logger.getInstance(SoaMethodJumpMarkerProvider.class);

    /**
     * 携程海豚图标 - 用于 SOA 日志跳转
     */
    public static final Icon METHOD_ICON = IconLoader.getIcon("/icons/methodIcon.svg", SoaMethodJumpMarkerProvider.class);

    /**
     * MOM API 变量组单例 —— 所有跳转共享同一个实例（共享缓存）
     */
    private static final MomApiVariableGroup MOM_API_GROUP = new MomApiVariableGroup();

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // 检查总开关是否启用
        SoaHelperSettings settings = SoaHelperSettings.getInstance();
        if (!settings.isEnabled()) {
            return null;
        }

        // 只处理方法标识符
        if (!(element instanceof PsiIdentifier)) {
            return null;
        }

        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiMethod method)) {
            return null;
        }

        // 检查是否为 SOA 方法
        if (!SoaMethodUtil.isSoaMethod(method)) {
            return null;
        }

        Project project = element.getProject();
        Module module = ModuleUtilCore.findModuleForPsiElement(element);

        if (module == null) {
            return null;
        }

        String methodName = method.getName();

        return new LineMarkerInfo<>(element, element.getTextRange(), METHOD_ICON, psiElement -> "跳转到日志查看",
                (e, elt) -> {
                    // 获取 appId
                    String appId = AppIdUtil.getAppId(module);
                    if (appId == null || appId.isEmpty()) {
                        LOG.warn("SOA jump aborted: appId not found, method=" + methodName
                                + ", module=" + module.getName());
                        NotifyUtil.showError(project, "无法找到 app.properties 或 app.id 配置");
                        return;
                    }

                    LOG.info("SOA jump click: appId=" + appId + ", methodName=" + methodName
                            + ", module=" + module.getName());

                    // 创建环境选择的弹出菜单
                    showEnvironmentPopup(e, project, appId, methodName);
                }, GutterIconRenderer.Alignment.RIGHT, () -> "SOA Jump");
    }

    /**
     * 显示跳转选项弹出菜单
     */
    private void showEnvironmentPopup(MouseEvent mouseEvent, Project project, String appId, String methodName) {
        // 获取启用的跳转选项
        SoaHelperSettings settings = SoaHelperSettings.getInstance();
        List<JumpOption> jumpOptions = settings.getEnabledJumpOptions();
        LOG.debug("SOA jump options loaded: appId=" + appId + ", methodName=" + methodName
                + ", enabledOptionCount=" + jumpOptions.size());

        if (jumpOptions.isEmpty()) {
            NotifyUtil.showError(project, "未配置跳转项，请前往“设置->SOA Helper”配置跳转项");
            return;
        }

        // 如果只有一个跳转选项，直接跳转
        if (jumpOptions.size() == 1) {
            JumpOption option = jumpOptions.get(0);
            LOG.info("SOA jump direct route: option=" + option.getName());
            performJump(project, option.getUrlTemplate(), appId, methodName);
            return;
        }

        // 创建列表弹出菜单步骤
        BaseListPopupStep<JumpOption> step = new BaseListPopupStep<JumpOption>(null, jumpOptions) {
            @Override
            public @NotNull String getTextFor(JumpOption value) {
                return value.getName();
            }

            @Override
            public @Nullable PopupStep<?> onChosen(JumpOption selectedValue, boolean finalChoice) {
                return doFinalStep(() -> {
                    performJump(project, selectedValue.getUrlTemplate(), appId, methodName);
                });
            }
        };

        // 创建轻量级列表弹出菜单
        var popup = JBPopupFactory.getInstance().createListPopup(step);

        // 在鼠标点击位置显示弹出菜单
        Component component = mouseEvent.getComponent();
        if (component != null) {
            Point point = mouseEvent.getPoint();
            Point screenPoint = new Point(point);
            SwingUtilities.convertPointToScreen(screenPoint, component);
            popup.showInScreenCoordinates(component, screenPoint);
        } else {
            popup.showInFocusCenter();
        }
    }

    /**
     * 执行跳转 —— 按需懒加载扩展变量。
     * 三条路径：
     * 1. 快速路径：模板没用扩展变量 → 直接跳转
     * 2. 缓存路径：用了扩展变量，缓存命中 → 直接跳转
     * 3. 异步路径：用了扩展变量，缓存未命中 → 进度框 + 后台请求
     */
    private void performJump(Project project, String urlTemplate, String appId, String methodName) {
        LOG.info("SOA jump start: appId=" + appId + ", methodName=" + methodName
                + ", template=" + urlTemplate);

        Map<String, String> basicVars = new HashMap<>();
        basicVars.put("appId", appId);
        basicVars.put("methodName", methodName);

        // 构建 Registry
        VariableGroupRegistry registry = new VariableGroupRegistry();
        registry.register(new BasicVariableGroup(appId, methodName));
        registry.register(MOM_API_GROUP);

        TemplateResolver resolver = new TemplateResolver(registry);

        // 判断是否需要异步加载
        if (!resolver.needsAsyncResolve(urlTemplate, basicVars)) {
            // 快速路径 或 缓存路径：同步解析
            try {
                String url = resolver.resolve(urlTemplate, basicVars);
                LOG.info("SOA jump resolved sync: url=" + url);
                BrowserUtil.browse(url);
            } catch (VariableResolveException e) {
                LOG.warn("SOA jump sync resolve failed: appId=" + appId + ", methodName=" + methodName
                        + ", message=" + e.getMessage(), e);
                NotifyUtil.showError(project, "跳转失败：" + e.getMessage());
            }
            return;
        }

        LOG.info("SOA jump requires async extended fields: appId=" + appId + ", methodName=" + methodName);

        // 异步路径：显示进度对话框
        ProgressManager.getInstance().run(new Task.Modal(project, "正在获取扩展字段...", true) {
            private String resolvedUrl;
            private VariableResolveException error;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    resolvedUrl = resolver.resolve(urlTemplate, basicVars);
                } catch (VariableResolveException e) {
                    error = e;
                }
            }

            @Override
            public void onSuccess() {
                if (resolvedUrl != null) {
                    LOG.info("SOA jump resolved async: url=" + resolvedUrl);
                    BrowserUtil.browse(resolvedUrl);
                } else if (error != null) {
                    LOG.warn("SOA jump async resolve failed: appId=" + appId + ", methodName=" + methodName
                            + ", message=" + error.getMessage(), error);
                    NotifyUtil.showError(project, "扩展字段获取失败：" + error.getMessage());
                }
            }

            @Override
            public void onCancel() {
                // 用户取消，静默退出
            }
        });
    }
}
