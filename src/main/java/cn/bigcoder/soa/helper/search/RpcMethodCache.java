package cn.bigcoder.soa.helper.search;

import cn.bigcoder.soa.helper.util.SoaMethodUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NotNull;

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

    private final Project project;
    private final Set<RpcMethodInfo> methodCache = ConcurrentHashMap.newKeySet();
    /**
     * 全量扫描后记录的已知 SOA 类文件路径，用于增量监听过滤
     */
    private final Set<String> trackedFilePaths = ConcurrentHashMap.newKeySet();
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
        // 使用 Task.Backgroundable 提供可取消的后台任务和进度显示
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在扫描 SOA 接口...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // 分批执行，每批都在独立的读操作中，避免长时间持有读锁
                scanRpcMethodsWithProgressBatched(indicator);
            }
        });
    }

    /**
     * 带进度指示器的扫描方法 - 分批处理，定期释放读锁
     */
    private void scanRpcMethodsWithProgressBatched(ProgressIndicator indicator) {
        methodCache.clear();
        trackedFilePaths.clear();
        
        // 触发soa服务加载前置钩子
        executeSoaMethodLoadBeforeHook();
        
        try {
            indicator.setText("正在获取项目中的所有类...");
            
            // 先获取所有类名（快速操作）
            String[] allClassNames = ApplicationManager.getApplication().runReadAction(
                (com.intellij.openapi.util.Computable<String[]>) () -> {
                    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
                    return cache.getAllClassNames();
                }
            );

            indicator.setText("soa-helper 正在扫描...");
            indicator.setIndeterminate(false);
            
            // 分批处理，每批100个类，处理完一批后释放读锁
            int batchSize = 100;
            for (int batchStart = 0; batchStart < allClassNames.length; batchStart += batchSize) {
                // 检查任务是否被取消
                if (indicator.isCanceled()) {
                    executeSoaMethodLoadAfterHook();
                    return;
                }
                
                int batchEnd = Math.min(batchStart + batchSize, allClassNames.length);
                int finalBatchStart = batchStart;
                
                // 每批在单独的读操作中处理，处理完立即释放读锁
                ApplicationManager.getApplication().runReadAction(() -> {
                    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
                    
                    for (int i = finalBatchStart; i < batchEnd; i++) {
                        // 更新进度
                        indicator.setFraction((double) i / allClassNames.length);
                        String className = allClassNames[i];
                        
                        // 使用ProjectScope.getContentScope(project)只扫描工作区中的类，不扫描jar包
                        PsiClass[] classes = cache.getClassesByName(className, ProjectScope.getContentScope(project));
                        for (PsiClass psiClass : classes) {
                            if (psiClass == null) {
                                continue;
                            }
                            // 额外检查：确保类文件在工作区中，而不是在外部依赖中
                            if (SoaMethodUtil.isSoaClass(psiClass)) {
                                indicator.setText2("发现 SOA 接口: " + className);
                                processRpcClass(psiClass);
                            }
                        }
                    }
                });
                // 读操作结束后，读锁被释放，UI 线程可以获取写锁进行操作
            }
            
            indicator.setText("扫描完成，共找到 " + methodCache.size() + " 个 SOA 方法");
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

    private void processRpcClass(PsiClass psiClass) {
        // 记录该 SOA 类的文件路径，用于后续增量监听
        trackedFilePaths.add(psiClass.getContainingFile().getVirtualFile().getPath());
        // 处理类中的所有方法
        for (PsiMethod method : psiClass.getMethods()) {
            // 使用宽松模式判断：有 Override 注解的方法默认为RPC方法
            // 因为分支切换时新版本jar包可能没加载好，只判断严格模式会遗漏方法
            if (SoaMethodUtil.isSoaMethodLoose(method)) {
                RpcMethodInfo methodInfo = new RpcMethodInfo(method.getName(), psiClass.getQualifiedName(),
                        method.getContainingFile().getVirtualFile().getPath(), method.getTextOffset());

                // 添加到缓存，使用方法名作为键，支持模糊搜索
                addToCache(methodInfo);
            }
        }
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
                String filePath = javaFile.getVirtualFile().getPath();
                for (PsiClass psiClass : javaFile.getClasses()) {
                    // 移除该类之前的所有方法
                    removeMethodsFromClass(psiClass.getQualifiedName());

                    // 如果是RPC实现类，重新添加其方法
                    if (SoaMethodUtil.isImplementBaijiContractAnnotatedInterface(psiClass)) {
                        processRpcClass(psiClass);
                    } else {
                        // 不再是 SOA 类，移除文件路径跟踪
                        trackedFilePaths.remove(filePath);
                    }
                }
            }
        } catch (IndexNotReadyException e) {
            // 索引未就绪时，不抛出异常，等待索引就绪后重试
            executeStartLoadIndexHook();
        } catch (Exception e) {
            // 捕获其他异常（如 Outdated stub），静默处理
            // 这些情况通常是暂时的，下次文件变更会重新触发
        }
    }

    private void removeMethodsFromClass(String className) {
        if (className == null) {
            return;
        }
        // 修复：实际移除缓存中指定类的方法
        methodCache.removeIf(method -> className.equals(method.className()));
    }

    /**
     * 判断文件是否是已知的 SOA 类文件
     */
    public boolean isTrackedFile(String filePath) {
        return trackedFilePaths.contains(filePath);
    }

    /**
     * 移除已跟踪的文件及其缓存（用于文件删除场景）
     */
    public void removeTrackedFile(String filePath) {
        trackedFilePaths.remove(filePath);
        methodCache.removeIf(method -> filePath.equals(method.filePath()));
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