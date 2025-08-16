package cn.bigcoder.soa.helper.util;

import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

/**
 * 项目内导航工具类
 */
public class NavigationUtil {


    /**
     * 导航到指定文件中的指定偏移量位置
     *
     * @param project 项目实例
     * @param filePath 文件路径
     * @param textOffset 文件中的偏移量
     */
    public static void navigateToMethod(Project project, String filePath, int textOffset) {
        // 检查参数是否为空
        if (project == null || project.isDisposed() || filePath == null) {
            return;
        }

        // 通过文件路径获取 VirtualFile
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath);
        if (virtualFile == null) {
            return;
        }

        // 通过 VirtualFile 获取 PsiFile
        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        if (psiFile == null) {
            return;
        }

        // 确保偏移量在有效范围内
        if (textOffset >= 0 && textOffset <= psiFile.getTextLength()) {
            // 获取偏移位置的元素
            PsiElement elementAt = psiFile.findElementAt(textOffset);
            if (elementAt != null) {
                // 确保在EDT线程中执行导航
                ApplicationManager.getApplication().invokeLater(() -> {
                    // 检查元素是否实现了NavigationItem接口
                    if (elementAt instanceof NavigationItem) {
                        // 使用NavigationItem的导航方法
                        ((NavigationItem) elementAt).navigate(true);
                    } else {
                        // 对于不支持NavigationItem的元素，使用文件描述符导航
                        OpenFileDescriptor descriptor = new OpenFileDescriptor(
                                project,
                                virtualFile,
                                textOffset
                        );
                        descriptor.navigate(true);
                    }
                });
                return;
            }
        }

        // 偏移量无效时，尝试使用行号导航
        navigateToLine(project, virtualFile, textOffset);
    }

    /**
     * 导航到指定行
     * @param project 项目实例
     * @param virtualFile 虚拟文件实例
     * @param lineNumber 行号
     */
    private static void navigateToLine(Project project, VirtualFile virtualFile, int lineNumber) {
        if (project == null || virtualFile == null || lineNumber < 1) {
            return;
        }
        // 行号在API中是从0开始的，所以需要减1
        OpenFileDescriptor descriptor = new OpenFileDescriptor(
                project,
                virtualFile,
                lineNumber - 1,
                0
        );
        ApplicationManager.getApplication().invokeLater(() -> {
            descriptor.navigate(true);
        });
    }
}
