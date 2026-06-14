package cn.bigcoder.soa.helper.util;

import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Collection;

/**
 * 方法调用参数解析器
 * 将方法调用的参数解析为静态字符串值
 *
 * 支持的解析场景：
 * 1. 字符串字面量："abc" → "abc"
 * 2. 变量引用：logTitle → 追溯变量初始化值
 * 3. 字符串拼接："response: " + xxx → 提取静态前缀 "response: "
 * 4. String.format("pattern: %s", ...) → 提取 % 之前的文本 "pattern: "
 * 5. 括号表达式：(expr) → 递归解析内部表达式
 * 6. 方法参数跨层追溯：最多向外追溯3层方法调用
 * 7. 无法解析 → 返回空字符串 ""
 */
public class MethodCallParamResolver {

    /**
     * 跨方法追溯的最大深度
     */
    private static final int MAX_CALLER_DEPTH = 3;

    /**
     * 解析方法调用表达式的第 index 个参数为字符串
     *
     * @param callExpression 方法调用表达式
     * @param index          参数索引（从0开始）
     * @return 解析后的字符串值，无法解析时返回空字符串
     */
    public static String resolveParam(PsiMethodCallExpression callExpression, int index) {
        PsiExpressionList argList = callExpression.getArgumentList();
        PsiExpression[] args = argList.getExpressions();
        if (index < 0 || index >= args.length) {
            return "";
        }
        return resolveExpressionWithDepth(args[index], 0);
    }

    /**
     * 解析表达式为字符串值
     */
    public static String resolveExpression(PsiExpression expression) {
        return resolveExpressionWithDepth(expression, 0);
    }

    /**
     * 带深度限制的表达式解析
     *
     * @param expression 表达式
     * @param callerDepth 当前跨方法追溯深度
     * @return 解析后的字符串值
     */
    private static String resolveExpressionWithDepth(PsiExpression expression, int callerDepth) {
        if (expression == null) {
            return "";
        }

        // 1. 字符串字面量
        if (expression instanceof PsiLiteralExpression literal) {
            Object value = literal.getValue();
            return value != null ? value.toString() : "";
        }

        // 2. 变量引用 - 追溯初始化值或方法参数
        if (expression instanceof PsiReferenceExpression refExpr) {
            PsiElement resolved = refExpr.resolve();
            if (resolved instanceof PsiVariable variable) {
                // 2a. 局部变量/字段 - 追溯初始化值
                PsiExpression initializer = variable.getInitializer();
                if (initializer != null) {
                    return resolveExpressionWithDepth(initializer, callerDepth);
                }
                // 2b. 方法参数 - 向上追溯调用方
                if (resolved instanceof PsiParameter parameter && callerDepth < MAX_CALLER_DEPTH) {
                    return resolveParameterFromCaller(parameter, callerDepth);
                }
            }
            return "";
        }

        // 3. 多元表达式（字符串拼接）a + b + c
        if (expression instanceof PsiPolyadicExpression polyadic) {
            return resolvePolyadicExpressionWithDepth(polyadic, callerDepth);
        }

        // 4. 方法调用表达式 - 处理 String.format 等
        if (expression instanceof PsiMethodCallExpression methodCall) {
            return resolveMethodCallExpressionWithDepth(methodCall, callerDepth);
        }

        // 5. 括号表达式 (expr)
        if (expression instanceof PsiParenthesizedExpression paren) {
            PsiExpression inner = paren.getExpression();
            return resolveExpressionWithDepth(inner, callerDepth);
        }

        return "";
    }

    /**
     * 解析方法参数：从调用方追溯参数的实际值
     *
     * 例如：
     * method() {
     *     String logTitle = "hello";
     *     methodA(1213, logTitle, 821);
     * }
     * methodA(a, logTitle, c) {
     *     methodB(logTitle);
     * }
     * methodB(logTitle) {
     *     CLogger.Info(logTitle, "xxx", new HashMap<>());  // 需要解析logTitle为"hello"
     * }
     *
     * @param parameter   方法参数
     * @param callerDepth 当前追溯深度
     * @return 解析后的字符串值
     */
    private static String resolveParameterFromCaller(PsiParameter parameter, int callerDepth) {
        // 获取参数所在的方法
        PsiElement parent = parameter.getDeclarationScope();
        if (!(parent instanceof PsiMethod method)) {
            return "";
        }

        // 获取参数在方法签名中的索引
        PsiParameterList parameterList = method.getParameterList();
        int paramIndex = parameterList.getParameterIndex(parameter);
        if (paramIndex < 0) {
            return "";
        }

        // 在包含此方法的文件范围内搜索方法的调用点
        PsiFile containingFile = method.getContainingFile();
        if (containingFile == null) {
            return "";
        }

        // 使用 ReferencesSearch 在当前文件范围内搜索方法引用
        Collection<PsiReference> references = ReferencesSearch.search(
                method, new LocalSearchScope(containingFile)
        ).findAll();

        for (PsiReference reference : references) {
            PsiElement element = reference.getElement();
            // 找到方法调用表达式
            PsiMethodCallExpression callerCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
            if (callerCall == null) {
                continue;
            }

            PsiExpressionList argList = callerCall.getArgumentList();
            PsiExpression[] args = argList.getExpressions();
            if (paramIndex >= args.length) {
                continue;
            }

            // 递归解析调用方传入的实际参数，深度+1
            String result = resolveExpressionWithDepth(args[paramIndex], callerDepth + 1);
            if (!result.isEmpty()) {
                return result;
            }
        }

        return "";
    }

    /**
     * 解析多元表达式（字符串拼接），提取最大已知静态前缀
     * 例如：
     * - "response: " + JsonUtil.toJSON(response) → "response: "
     * - "prefix" + "suffix" → "prefixsuffix"
     * - variable + "text" → ""（变量在前无法确定前缀）
     */
    private static String resolvePolyadicExpressionWithDepth(PsiPolyadicExpression polyadic, int callerDepth) {
        // 只处理 + 运算符（字符串拼接）
        if (polyadic.getOperationTokenType() != com.intellij.psi.JavaTokenType.PLUS) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (PsiExpression operand : polyadic.getOperands()) {
            String value = resolveExpressionWithDepth(operand, callerDepth);
            if (value.isEmpty()) {
                // 遇到不可解析的部分，返回已收集的前缀
                break;
            }
            result.append(value);
        }
        return result.toString();
    }

    /**
     * 解析方法调用表达式
     * 特别处理 String.format("pattern %s", ...) 情况
     */
    private static String resolveMethodCallExpressionWithDepth(PsiMethodCallExpression methodCall, int callerDepth) {
        PsiReferenceExpression methodRef = methodCall.getMethodExpression();
        String methodName = methodRef.getReferenceName();

        // 处理 String.format(pattern, args...)
        if ("format".equals(methodName)) {
            PsiExpression qualifier = methodRef.getQualifierExpression();
            if (qualifier != null && "String".equals(qualifier.getText())) {
                PsiExpression[] args = methodCall.getArgumentList().getExpressions();
                if (args.length > 0) {
                    String pattern = resolveExpressionWithDepth(args[0], callerDepth);
                    return extractBeforeFormatSpecifier(pattern);
                }
            }
        }

        return "";
    }

    /**
     * 从 String.format 模式中提取第一个格式占位符之前的文本
     * 例如：
     * - "response: %s" → "response: "
     * - "%s is error" → ""
     * - "no format" → "no format"
     * - "100%% done %s" → "100% done "（%% 是转义的 %）
     */
    private static String extractBeforeFormatSpecifier(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '%') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '%') {
                    // %% 转义为 %
                    result.append('%');
                    i += 2;
                } else {
                    // 遇到格式占位符，停止
                    break;
                }
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }
}
