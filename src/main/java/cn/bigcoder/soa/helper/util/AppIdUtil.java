package cn.bigcoder.soa.helper.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * AppId 工具类
 * 用于从模块的 resources/META-INF/app.properties 文件中读取 app.id
 */
public class AppIdUtil {

    /**
     * 从模块中获取 appId
     * 会在以下路径中查找 app.properties：
     * - src/main/resources/META-INF/app.properties
     * - resources/META-INF/app.properties
     * - META-INF/app.properties
     *
     * @param module IDEA 模块
     * @return appId，如果找不到返回 null
     */
    public static String getAppId(Module module) {
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
            // 如果是 resources 目录，直接查找 META-INF/app.properties
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
