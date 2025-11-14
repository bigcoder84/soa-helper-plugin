package cn.bigcoder.soa.helper.marker;

import cn.bigcoder.soa.helper.settings.JumpOption;
import cn.bigcoder.soa.helper.util.SoaMethodUtil;
import cn.bigcoder.soa.helper.settings.SoaHelperSettings;
import cn.bigcoder.soa.helper.util.TemplateParser;
import cn.bigcoder.soa.helper.util.AppIdUtil;
import cn.bigcoder.soa.helper.util.NotifyUtil;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
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
public class SoaLogLineMarkerProvider implements LineMarkerProvider {

    /**
     * 携程海豚图标 - 用于 SOA 日志跳转
     */
    public static final Icon METHOD_ICON = IconLoader.getIcon("/icons/methodIcon.svg", SoaLogLineMarkerProvider.class);

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
                        NotifyUtil.showError(project, "无法找到 app.properties 或 app.id 配置");
                        return;
                    }

                    // 创建环境选择的弹出菜单
                    showEnvironmentPopup(e, project, appId, methodName);
                }, GutterIconRenderer.Alignment.RIGHT, () -> "SOA Log Jump");
    }

    /**
     * 显示跳转选项弹出菜单
     */
    private void showEnvironmentPopup(MouseEvent mouseEvent, Project project, String appId, String methodName) {
        // 获取启用的跳转选项
        SoaHelperSettings settings = SoaHelperSettings.getInstance();
        List<JumpOption> jumpOptions = settings.getEnabledJumpOptions();

        if (jumpOptions.isEmpty()) {
            NotifyUtil.showError(project, "未配置跳转项，请前往“设置->SOA Helper”配置跳转项");
            return;
        }

        // 如果只有一个跳转选项，直接跳转
        if (jumpOptions.size() == 1) {
            JumpOption option = jumpOptions.get(0);
            String url = buildUrlFromTemplate(option.getUrlTemplate(), appId, methodName);
            BrowserUtil.browse(url);
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
                    // 使用模板解析器构建 URL
                    String url = buildUrlFromTemplate(selectedValue.getUrlTemplate(), appId, methodName);
                    BrowserUtil.browse(url);
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
     * 根据模板构建 URL
     *
     * @param template URL 模板
     * @param appId 应用 ID
     * @param methodName 方法名
     * @return 构建后的 URL
     */
    private String buildUrlFromTemplate(String template, String appId, String methodName) {
        Map<String, String> variables = new HashMap<>();
        variables.put("appId", appId);
        variables.put("methodName", methodName);

        TemplateParser parser = new TemplateParser();
        return parser.parse(template, variables);
    }
}

