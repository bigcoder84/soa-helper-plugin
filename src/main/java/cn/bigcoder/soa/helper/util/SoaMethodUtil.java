package cn.bigcoder.soa.helper.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

/**
 * SOA 方法判断工具类
 * 统一管理判断方法是否为 SOA RPC 方法的逻辑
 */
public class SoaMethodUtil {

    public static final String BAIJI_CONTRACT_CLASS_NAME = "com.ctriposs.baiji.rpc.common.BaijiContract";

    /**
     * 判断方法是否为 SOA RPC 方法
     *
     * 判断标准：
     * 1. 方法所在类实现了带有 @BaijiContract 注解的接口
     * 2. 方法在该接口中定义（通过方法名匹配）
     * 3. 或者方法有 @Override 注解且类实现了 @BaijiContract 接口（兼容性考虑）
     *
     * @param method 要判断的方法
     * @return 如果是 SOA RPC 方法返回 true，否则返回 false
     */
    public static boolean isSoaMethod(PsiMethod method) {
        if (method == null) {
            return false;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        // 检查是否有带 @BaijiContract 注解的接口
        boolean hasAnnotatedInterface = false;
        for (PsiClass interfaceClass : containingClass.getInterfaces()) {
            if (interfaceClass.hasAnnotation(BAIJI_CONTRACT_CLASS_NAME)) {
                hasAnnotatedInterface = true;

                // 检查方法是否在接口中定义
                for (PsiMethod interfaceMethod : interfaceClass.getMethods()) {
                    if (interfaceMethod.getName().equals(method.getName())) {
                        return true;
                    }
                }
            }
        }

        // 兼容性检查：如果有 @Override 注解且类实现了 @BaijiContract 接
        if (hasAnnotatedInterface && method.hasAnnotation("java.lang.Override")) {
            return true;
        }

        return false;
    }

    /**
     * 检查方法是否有 @Override 注解
     *
     * @param method 要检查的方法
     * @return 如果有 @Override 注解返回 true，否则返回 false
     */
    public static boolean hasOverrideAnnotation(PsiMethod method) {
        return method != null && method.hasAnnotation("java.lang.Override");
    }

    /**
     * 检查类是否实现了带有 @BaijiContract 注解的接口
     *
     * @param psiClass 要检查的类
     * @return 如果实现了带有 @BaijiContract 注解的接口返回 true，否则返回 false
     */
    public static boolean isImplementBaijiContractAnnotatedInterface(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }

        for (PsiClass iface : psiClass.getInterfaces()) {
            if (iface.hasAnnotation(BAIJI_CONTRACT_CLASS_NAME)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断方法是否为 SOA RPC 方法（宽松模式）
     * 只要有 @Override 注解就认为是 RPC 方法
     *
     * 注意：这种模式适用于索引构建场景，可以避免因 jar 包未加载完成导致的方法遗漏
     *
     * @param method 要判断的方法
     * @return 如果有 @Override 注解或满足严格条件返回 true，否则返回 false
     */
    public static boolean isSoaMethodLoose(PsiMethod method) {
        // 先检查是否有 @Override 注解（宽松模式）
        if (hasOverrideAnnotation(method)) {
            return true;
        }

        // 如果没有 @Override，使用严格判断
        return isSoaMethod(method);
    }

    /**
     * 判断类是否为 SOA 实现类
     *
     * @param psiClass
     * @return
     */
    public static boolean isSoaClass(PsiClass psiClass) {
        return isInWorkspace(psiClass) && isImplementBaijiContractAnnotatedInterface(psiClass);
    }

    /**
     * 检查类是否在工作区中（不是外部依赖或jar包中的类）
     *
     * @param psiClass 要检查的类
     * @return 如果类在工作区中返回true，否则返回false
     */
    private static boolean isInWorkspace(PsiClass psiClass) {
        if (psiClass == null || psiClass.getContainingFile() == null) {
            return false;
        }

        // 获取类的虚拟文件路径
        String filePath = psiClass.getContainingFile().getVirtualFile().getPath();

        // 检查是否为Java源文件（.java文件）且不在jar包、构建输出目录或IDE配置目录中
        return filePath.endsWith(".java") && !filePath.contains(".jar!") && !filePath.contains("/.idea/")
                && !filePath.contains("/out/") && !filePath.contains("/build/");
    }
}

