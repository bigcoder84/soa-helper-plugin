package cn.bigcoder.soa.helper.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiShortNamesCache;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RpcMethodCache {

    // 移除静态单例实例，改为按项目存储的实例映射
    private static final Map<Project, RpcMethodCache> instances = new ConcurrentHashMap<>();
    private static final String BAIJI_CONTRACT_CLASS_NAME = "com.ctriposs.baiji.rpc.common.BaijiContract";
    private final Project project;
    private final Set<RpcMethodInfo> methodCache = new HashSet<>();
    private boolean projectIndexReady;
    /**
     * 索引构建成功钩子
     */
    private final Map<String, IndexLoadHook> indexLoadHooks = new ConcurrentHashMap<>();

    public RpcMethodCache(Project project) {
        this.project = project;
    }

    public void initialize() {
        afterExecuteScan();
    }

    // 修改 getInstance 方法，按项目获取/创建实例
    public static RpcMethodCache getInstance(Project project) {
        // 为每个项目创建独立实例，避免跨项目缓存污染
        return instances.computeIfAbsent(project, RpcMethodCache::new);
    }

    public String registerIndexLoadHook(IndexLoadHook hook) {
        String randomId = UUID.randomUUID().toString();
        indexLoadHooks.put(randomId, hook);
        return randomId;
    }

    public void removeIndexLoadHook(String hookId) {
        indexLoadHooks.remove(hookId);
    }

    /**
     * 异步线程刷新soa服务索引
     */
    public void asyncScanRpcMethods() {
        RpcMethodCache thisCache = this;
        ApplicationManager.getApplication().executeOnPooledThread(() ->
                ApplicationManager.getApplication().runReadAction(thisCache::scanRpcMethods)
        );
    }

    public synchronized void scanRpcMethods() {
        methodCache.clear();
        // 触发soa服务加载前置钩子
        executeSoaMethodLoadBeforeHook();
        try {
            // 在 runWhenSmart 内部，索引已就绪
            JavaPsiFacade instance = JavaPsiFacade.getInstance(project);
            PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
            String[] allClassNames = cache.getAllClassNames();

            for (String className : allClassNames) {
                // 使用ProjectScope.getContentScope(project)只扫描工作区中的类，不扫描jar包
                PsiClass[] classes = cache.getClassesByName(className, ProjectScope.getContentScope(project));
                for (PsiClass psiClass : classes) {
                    if (psiClass == null) {
                        continue;
                    }
                    // 额外检查：确保类文件在工作区中，而不是在外部依赖中
                    if (isInWorkspace(psiClass) && hasAnnotatedInterface(psiClass, BAIJI_CONTRACT_CLASS_NAME)) {
                        processRpcClass(psiClass);
                    }
                }
            }
            // 触发soa服务加载后置钩子
            executeSoaMethodLoadAfterHook();
        } catch (IndexNotReadyException e) {
            executeSoaMethodLoadAfterHook();
            // 索引未就绪时，不抛出异常，等待索引就绪后重试
            executeStartLoadIndexHook();
        }
    }

    /**
     * 执行索引加载钩子
     */
    private void executeStartLoadIndexHook() {
        projectIndexReady = false;
        for (IndexLoadHook indexLoadHook : indexLoadHooks.values()) {
            indexLoadHook.beforeProjectIndexLoad();
        }
        afterExecuteScan();
    }

    /**
     * 执行索引加载钩子
     */
    private void executeSoaMethodLoadBeforeHook() {
        for (IndexLoadHook indexLoadHook : indexLoadHooks.values()) {
            indexLoadHook.beforeSoaMethodLoad();
        }
    }

    /**
     * 执行索引加载钩子
     */
    private void executeSoaMethodLoadAfterHook() {
        for (IndexLoadHook indexLoadHook : indexLoadHooks.values()) {
            indexLoadHook.afterSoaMethodLoad();
        }
    }



    /**
     * 检查类实现的接口是否带有指定注解
     *
     * @param psiClass 要检查的类
     * @return 如果类实现的接口带有指定注解则返回 true，否则返回 false
     */
    private boolean hasAnnotatedInterface(PsiClass psiClass, String annotaionQualifyName) {
        for (PsiClass iface : psiClass.getInterfaces()) {
            if (iface.hasAnnotation(annotaionQualifyName)) {
                return true;
            }
        }
        return false;
    }

    private void processRpcClass(PsiClass psiClass) {
        // 处理类中的所有方法
        for (PsiMethod method : psiClass.getMethods()) {
            // 有 Override 注解的方法默认为RPC方法，因为分支切换时新版本jar包可能没加载好，只判断isRpcMethod会遗漏方法
            boolean rpcMethod = isInterfaceMethod(method);
            if (!rpcMethod) {
                rpcMethod = isRpcMethod(method);
            }
            if (rpcMethod) {
                RpcMethodInfo methodInfo = new RpcMethodInfo(method.getName(), psiClass.getQualifiedName(),
                        method.getContainingFile().getVirtualFile().getPath(), method.getTextOffset());

                // 添加到缓存，使用方法名作为键，支持模糊搜索
                addToCache(methodInfo);

                // 也可以添加类名作为键，支持按类名搜索
                if (psiClass.getName() != null) {
                    addToCache(methodInfo);
                }
            }
        }
    }

    private boolean isInterfaceMethod(PsiMethod method) {
        PsiAnnotation annotation = method.getAnnotation("java.lang.Override");
        return annotation != null;
    }

    private boolean isRpcMethod(PsiMethod method) {
        // 检查方法是否来自带有BaijiContract注解的接口
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        for (PsiClass interfaceClass : containingClass.getInterfaces()) {
            if (interfaceClass.hasAnnotation(BAIJI_CONTRACT_CLASS_NAME)) {
                // 检查方法是否在接口中定义
                for (PsiMethod psiMethod : interfaceClass.getMethods()) {
                    if (psiMethod.getName().equals(method.getName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void addToCache(RpcMethodInfo methodInfo) {
        // 将关键词拆分为多个可能的搜索词，提高搜索灵敏度
        methodCache.add(methodInfo);
    }


    /**
     * 搜索对应方法
     *
     * @param query
     * @return
     */
    public List<RpcMethodInfo> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        query = query.toLowerCase();
        // 构建用于匹配不连续字符的正则表达式
        String regex = query.chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining(".*?"));
        Pattern pattern = Pattern.compile(regex);

        Set<RpcMethodInfo> results = new HashSet<>();

        // 搜索所有匹配正则表达式的缓存条目
        for (RpcMethodInfo rpcMethodInfo : methodCache) {
            String methodName = rpcMethodInfo.methodName().toLowerCase();
            Matcher matcher = pattern.matcher(methodName);
            if (matcher.find()) {
                results.add(rpcMethodInfo);
            }
        }

        // 按匹配度排序
        String finalQuery = query;
        return results.stream().sorted((m1, m2) -> {
            int score1 = calculateScore(m1, finalQuery);
            int score2 = calculateScore(m2, finalQuery);
            return Integer.compare(score2, score1); // 降序排列
        }).collect(Collectors.toList());
    }

    public List<RpcMethodInfo> searchAll() {
        return methodCache.stream().toList();
    }

    /**
     * 判断项目索引是否加载完成
     *
     * @return
     */
    public boolean isProjectIndexReady() {
        return projectIndexReady;
    }

    private int calculateScore(RpcMethodInfo method, String query) {
        int score = 0;
        String methodName = method.methodName().toLowerCase();

        // 计算编辑距离
        int editDistance = levenshteinDistance(methodName, query);
        int maxLength = Math.max(query.length(), methodName.length());
        score += (maxLength - editDistance) * 2;

        // 匹配位置越靠前，得分越高
        int index = methodName.indexOf(query);
        if (index != -1) {
            score += (methodName.length() - index) * 3;
        }

        // 前缀匹配加分
        if (methodName.startsWith(query)) {
            score += 10;
        }

        // 完全匹配加分
        if (methodName.equals(query)) {
            score += 20;
        }

        return score;
    }

    /**
     * 计算两个字符串的 Levenshtein 距离
     *
     * @param s1 第一个字符串
     * @param s2 第二个字符串
     * @return Levenshtein 距离
     */
    private int levenshteinDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[m][n];
    }

    // 当文件变化时更新缓存
    public void updateCacheForFile(PsiFile file) {
        try {
            if (file instanceof PsiJavaFile javaFile) {
                for (PsiClass psiClass : javaFile.getClasses()) {
                    // 移除该类之前的所有方法
                    removeMethodsFromClass(psiClass.getQualifiedName());

                    // 如果是RPC实现类，重新添加其方法
                    if (isRpcImplementationClass(psiClass)) {
                        processRpcClass(psiClass);
                    }
                }
            }
        } catch (IndexNotReadyException e) {
            // 索引未就绪时，不抛出异常，等待索引就绪后重试
            executeStartLoadIndexHook();
        }
    }

    private boolean isRpcImplementationClass(PsiClass psiClass) {
        for (PsiClass iface : psiClass.getInterfaces()) {
            if (iface.hasAnnotation(BAIJI_CONTRACT_CLASS_NAME)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查类是否在工作区中（不是外部依赖或jar包中的类）
     * @param psiClass 要检查的类
     * @return 如果类在工作区中返回true，否则返回false
     */
    private boolean isInWorkspace(PsiClass psiClass) {
        if (psiClass == null || psiClass.getContainingFile() == null) {
            return false;
        }
        
        // 获取类的虚拟文件路径
        String filePath = psiClass.getContainingFile().getVirtualFile().getPath();
        
        // 检查是否为Java源文件（.java文件）且不在jar包、构建输出目录或IDE配置目录中
        return filePath.endsWith(".java") && 
               !filePath.contains(".jar!") && 
               !filePath.contains("/.idea/") &&
               !filePath.contains("/out/") &&
               !filePath.contains("/build/");
    }

    private void removeMethodsFromClass(String className) {
        if (className == null) {
            return;
        }
        // 修复：实际移除缓存中指定类的方法
        methodCache.removeIf(method -> className.equals(method.className()));
    }

    /**
     * 索引加载完成后执行方法扫描
     */
    private void afterExecuteScan() {
        RpcMethodCache thisCache = this;
        DumbService.getInstance(project).runWhenSmart(() -> {
            projectIndexReady = true;
            for (IndexLoadHook indexLoadHook : indexLoadHooks.values()) {
                indexLoadHook.afterProjectIndexLoad();
            }
            thisCache.asyncScanRpcMethods();
        });
    }
}