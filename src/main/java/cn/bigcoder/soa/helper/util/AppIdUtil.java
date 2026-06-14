package cn.bigcoder.soa.helper.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * AppId 工具类
 * 用于从模块的 resources/META-INF/app.properties 文件中读取 app.id
 */
public class AppIdUtil {

    /**
     * 封装 appId 及其来源模块信息
     */
    public record AppIdInfo(String appId, String moduleName) {
        @Override
        public String toString() {
            return moduleName + " (" + appId + ")";
        }
    }

    /**
     * 从模块中获取 appId（原有方法，保持不变）
     * 会在以下路径中查找 app.properties：
     * - src/main/resources/META-INF/app.properties
     * - resources/META-INF/app.properties
     * - META-INF/app.properties
     *
     * @param module IDEA 模块
     * @return appId，如果找不到返回 null
     */
    public static String getAppId(Module module) {
        return findAppIdInModule(module);
    }

    /**
     * 获取模块关联的所有 appId（支持多模块项目）
     * <p>
     * 1. 首先在当前模块中查找 app.properties
     * 2. 如果当前模块没有，则通过反向依赖分析找到所有依赖当前模块且拥有 app.properties 的模块
     *
     * @param module IDEA 模块
     * @return 关联的 AppIdInfo 列表，可能为空
     */
    public static List<AppIdInfo> getAppIds(Module module) {
        if (module == null) {
            return List.of();
        }

        // 先在当前模块中查找
        String directAppId = findAppIdInModule(module);
        if (directAppId != null && !directAppId.isEmpty()) {
            return List.of(new AppIdInfo(directAppId, module.getName()));
        }

        // 当前模块没有 app.properties，通过反向依赖分析查找
        return findAppIdsByReverseDependency(module);
    }

    /**
     * 在指定模块中直接查找 app.id
     */
    private static String findAppIdInModule(Module module) {
        if (module == null) {
            return null;
        }

        // 获取模块的内容根目录
        VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();

        for (VirtualFile contentRoot : contentRoots) {
            // 尝试多个可能的路径
            String[] possiblePaths = {
                    "src/main/resources/META-INF/app.properties",
                    "resources/META-INF/app.properties",
                    "META-INF/app.properties"
            };

            for (String path : possiblePaths) {
                VirtualFile propertiesFile = contentRoot.findFileByRelativePath(path);
                if (propertiesFile != null && propertiesFile.exists()) {
                    String appId = readAppIdFromFile(propertiesFile);
                    if (appId != null && !appId.isEmpty()) {
                        return appId;
                    }
                }
            }
        }

        // 如果在内容根目录找不到，尝试在源根目录查找
        VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
        for (VirtualFile sourceRoot : sourceRoots) {
            VirtualFile propertiesFile = sourceRoot.findFileByRelativePath("META-INF/app.properties");
            if (propertiesFile != null && propertiesFile.exists()) {
                String appId = readAppIdFromFile(propertiesFile);
                if (appId != null && !appId.isEmpty()) {
                    return appId;
                }
            }
        }

        return null;
    }

    /**
     * 通过反向依赖分析查找所有拥有 app.properties 且依赖当前模块的模块
     */
    private static List<AppIdInfo> findAppIdsByReverseDependency(Module targetModule) {
        Project project = targetModule.getProject();
        Module[] allModules = ModuleManager.getInstance(project).getModules();
        List<AppIdInfo> result = new ArrayList<>();

        for (Module candidate : allModules) {
            // 跳过自身
            if (candidate.equals(targetModule)) {
                continue;
            }

            // 候选模块必须自身有 app.properties
            String candidateAppId = findAppIdInModule(candidate);
            if (candidateAppId == null || candidateAppId.isEmpty()) {
                continue;
            }

            // 检查候选模块是否（直接或间接）依赖目标模块
            if (isDependentOn(candidate, targetModule)) {
                result.add(new AppIdInfo(candidateAppId, candidate.getName()));
            }
        }

        return result;
    }

    /**
     * 递归检查 source 模块是否直接或间接依赖 target 模块
     */
    private static boolean isDependentOn(Module source, Module target) {
        return isDependentOn(source, target, new java.util.HashSet<>());
    }

    private static boolean isDependentOn(Module source, Module target, java.util.Set<Module> visited) {
        if (!visited.add(source)) {
            return false; // 防止循环依赖导致无限递归
        }

        Module[] dependencies = ModuleRootManager.getInstance(source).getDependencies();
        for (Module dep : dependencies) {
            if (dep.equals(target)) {
                return true;
            }
            if (isDependentOn(dep, target, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从文件中读取 app.id 属性
     *
     * @param propertiesFile properties 文件
     * @return app.id 的值，如果读取失败返回 null
     */
    private static String readAppIdFromFile(VirtualFile propertiesFile) {
        try (InputStream inputStream = propertiesFile.getInputStream()) {
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties.getProperty("app.id");
        } catch (IOException e) {
            return null;
        }
    }
}
