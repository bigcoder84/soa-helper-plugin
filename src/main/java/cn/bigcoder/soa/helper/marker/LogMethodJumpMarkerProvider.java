package cn.bigcoder.soa.helper.marker;

import cn.bigcoder.soa.helper.settings.LogJumpOption;
import cn.bigcoder.soa.helper.settings.SoaHelperSettings;
import cn.bigcoder.soa.helper.util.AppIdUtil;
import cn.bigcoder.soa.helper.util.AppIdUtil.AppIdInfo;
import cn.bigcoder.soa.helper.util.MethodCallParamResolver;
import cn.bigcoder.soa.helper.util.NotifyUtil;
import cn.bigcoder.soa.helper.util.TemplateParser;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志方法快速跳转的 LineMarker 提供者
 * 在匹配配置的静态方法调用处（如 CLogger.Info(...)）显示图标，
 * 点击后根据 URL 模板打开浏览器
 */
public class LogMethodJumpMarkerProvider implements LineMarkerProvider {

    /**
     * 日志跳转图标
     */
    public static final Icon LOG_ICON = IconLoader.getIcon("/icons/logMarker.svg", LogMethodJumpMarkerProvider.class);

    /**
     * 匹配 ${params[N]} 模式的正则
     */
    private static final Pattern PARAMS_PATTERN = Pattern.compile("\\$\\{params\\[(\\d+)]}");

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // 检查总开关
        SoaHelperSettings settings = SoaHelperSettings.getInstance();
        if (!settings.isEnabled()) {
            return null;
        }

        // 只处理 PsiIdentifier
        if (!(element instanceof PsiIdentifier)) {
            return null;
        }

        PsiElement parent = element.getParent();

        // 需要是方法调用引用表达式的名称部分
        if (!(parent instanceof PsiReferenceExpression refExpr)) {
            return null;
        }

        PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression callExpression)) {
            return null;
        }

        // 确保该 PsiIdentifier 是引用名称（方法名部分），而不是 qualifier 部分
        if (refExpr.getReferenceNameElement() != element) {
            return null;
        }

        // 解析被调用的方法
        PsiMethod resolvedMethod = callExpression.resolveMethod();
        if (resolvedMethod == null) {
            return null;
        }

        // 获取匹配的 LogJumpOptions
        List<LogJumpOption> matchedOptions = findMatchingOptions(settings, resolvedMethod);
        if (matchedOptions.isEmpty()) {
            return null;
        }

        Project project = element.getProject();
        Module module = ModuleUtilCore.findModuleForPsiElement(element);

        if (module == null) {
            return null;
        }

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                LOG_ICON,
                psiElement -> "跳转到日志查看",
                (e, elt) -> handleClick(e, elt, project, module, matchedOptions),
                GutterIconRenderer.Alignment.RIGHT,
                () -> "Log Jump"
        );
    }

    /**
     * 处理图标点击事件
     */
    private void handleClick(MouseEvent mouseEvent, PsiElement element, Project project,
                             Module module, List<LogJumpOption> matchedOptions) {
        // 获取关联的 appId 列表（支持多模块项目）
        List<AppIdInfo> appIdInfos = AppIdUtil.getAppIds(module);
        if (appIdInfos.isEmpty()) {
            NotifyUtil.showError(project, "无法找到 app.properties 或 app.id 配置");
            return;
        }

        // 获取方法调用表达式以解析参数
        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiReferenceExpression)) {
            return;
        }
        PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression callExpression)) {
            return;
        }

        if (appIdInfos.size() == 1) {
            // 单个 appId，直接进入选项流程
            proceedWithAppId(mouseEvent, project, appIdInfos.get(0).appId(), callExpression, matchedOptions);
        } else {
            // 多个 appId，先让用户选择
            showAppIdPopup(mouseEvent, project, appIdInfos, callExpression, matchedOptions);
        }
    }

    /**
     * 显示 appId 选择弹出菜单（多模块场景）
     */
    private void showAppIdPopup(MouseEvent mouseEvent, Project project, List<AppIdInfo> appIdInfos,
                                PsiMethodCallExpression callExpression, List<LogJumpOption> matchedOptions) {
        BaseListPopupStep<AppIdInfo> step = new BaseListPopupStep<>("选择 AppId", appIdInfos) {
            @Override
            public @NotNull String getTextFor(AppIdInfo value) {
                return value.toString();
            }

            @Override
            public @Nullable PopupStep<?> onChosen(AppIdInfo selectedValue, boolean finalChoice) {
                if (matchedOptions.size() == 1) {
                    // 只有一个跳转选项，直接打开
                    return doFinalStep(() -> {
                        String url = buildUrl(matchedOptions.get(0).getUrlTemplate(), selectedValue.appId(), callExpression);
                        BrowserUtil.browse(url);
                    });
                } else {
                    // 多个跳转选项，显示子菜单
                    return createOptionStep(selectedValue.appId(), callExpression, matchedOptions);
                }
            }
        };

        showPopupAtMouse(mouseEvent, JBPopupFactory.getInstance().createListPopup(step));
    }

    /**
     * 使用选定的 appId 继续处理跳转
     */
    private void proceedWithAppId(MouseEvent mouseEvent, Project project, String appId,
                                  PsiMethodCallExpression callExpression, List<LogJumpOption> matchedOptions) {
        if (matchedOptions.size() == 1) {
            // 单个选项直接跳转
            LogJumpOption option = matchedOptions.get(0);
            String url = buildUrl(option.getUrlTemplate(), appId, callExpression);
            BrowserUtil.browse(url);
        } else {
            // 多个选项显示弹出菜单
            showOptionPopup(mouseEvent, appId, callExpression, matchedOptions);
        }
    }

    /**
     * 创建跳转选项的 PopupStep（用于 appId 选择后的子菜单）
     */
    private BaseListPopupStep<LogJumpOption> createOptionStep(String appId,
                                                              PsiMethodCallExpression callExpression,
                                                              List<LogJumpOption> options) {
        return new BaseListPopupStep<>(null, options) {
            @Override
            public @NotNull String getTextFor(LogJumpOption value) {
                return value.getName();
            }

            @Override
            public @Nullable PopupStep<?> onChosen(LogJumpOption selectedValue, boolean finalChoice) {
                return doFinalStep(() -> {
                    String url = buildUrl(selectedValue.getUrlTemplate(), appId, callExpression);
                    BrowserUtil.browse(url);
                });
            }
        };
    }

    /**
     * 显示跳转选项弹出菜单（单 appId 多选项场景）
     */
    private void showOptionPopup(MouseEvent mouseEvent, String appId,
                                 PsiMethodCallExpression callExpression, List<LogJumpOption> options) {
        BaseListPopupStep<LogJumpOption> step = createOptionStep(appId, callExpression, options);
        showPopupAtMouse(mouseEvent, JBPopupFactory.getInstance().createListPopup(step));
    }

    /**
     * 在鼠标位置显示弹出菜单
     */
    private void showPopupAtMouse(MouseEvent mouseEvent, ListPopup popup) {
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
     * 构建最终 URL
     * 预处理 ${params[N]} → ${params_N} 以兼容 TemplateParser 的标识符规则
     */
    private String buildUrl(String template, String appId, PsiMethodCallExpression callExpression) {
        Map<String, String> variables = new HashMap<>();
        variables.put("appId", appId);

        // 找到模板中所有 ${params[N]} 引用，解析对应的参数值
        Matcher matcher = PARAMS_PATTERN.matcher(template);
        while (matcher.find()) {
            int paramIndex = Integer.parseInt(matcher.group(1));
            String paramValue = MethodCallParamResolver.resolveParam(callExpression, paramIndex);
            variables.put("params_" + paramIndex, paramValue);
        }

        // 预处理模板：${params[N]} → ${params_N}
        String normalizedTemplate = PARAMS_PATTERN.matcher(template).replaceAll("\\${params_$1}");

        TemplateParser parser = new TemplateParser();
        String url = parser.parse(normalizedTemplate, variables);
        System.out.println("[SOA-Helper-Debug] Generated URL: " + url);
        return url;
    }

    /**
     * 查找匹配给定方法的所有启用的 LogJumpOption
     */
    private List<LogJumpOption> findMatchingOptions(SoaHelperSettings settings, PsiMethod method) {
        List<LogJumpOption> enabledOptions = settings.getEnabledLogJumpOptions();
        List<LogJumpOption> matched = new ArrayList<>();

        // 获取方法的完全限定类名和方法名
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return matched;
        }

        String methodName = method.getName();
        String qualifiedClassName = containingClass.getQualifiedName();
        String simpleClassName = containingClass.getName();

        for (LogJumpOption option : enabledOptions) {
            if (matchesAnyPattern(option.getMethodPatterns(), qualifiedClassName, simpleClassName, methodName)) {
                matched.add(option);
            }
        }

        return matched;
    }

    /**
     * 检查方法是否匹配任一模式
     * 模式格式为 ClassName#MethodName，支持全限定名和简单类名
     */
    private boolean matchesAnyPattern(List<String> patterns, String qualifiedClassName,
                                      String simpleClassName, String methodName) {
        if (patterns == null) {
            return false;
        }

        for (String pattern : patterns) {
            if (pattern == null || !pattern.contains("#")) {
                continue;
            }
            String[] parts = pattern.split("#", 2);
            String patternClass = parts[0].trim();
            String patternMethod = parts[1].trim();

            if (!patternMethod.equals(methodName)) {
                continue;
            }

            // 全限定类名匹配
            if (qualifiedClassName != null && qualifiedClassName.equals(patternClass)) {
                return true;
            }

            // 简单类名匹配
            if (simpleClassName != null && simpleClassName.equals(patternClass)) {
                return true;
            }
        }

        return false;
    }
}
