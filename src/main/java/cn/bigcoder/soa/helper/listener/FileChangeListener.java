package cn.bigcoder.soa.helper.listener;

import cn.bigcoder.soa.helper.search.RpcMethodCache;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FileChangeListener implements BulkFileListener {

    private final Project project;
    private final RpcMethodCache cache;

    public FileChangeListener(Project project, RpcMethodCache cache) {
        this.project = project;
        this.cache = cache;
        // 通过全局消息总线订阅
        project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, this);
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
            VirtualFile file = event.getFile();
            if (file == null || !cache.isTrackedFile(file.getPath())) {
                continue;
            }

            if (event instanceof VFileDeleteEvent) {
                // 文件删除：移除缓存和跟踪
                cache.removeTrackedFile(file.getPath());
            } else if (event instanceof VFileContentChangeEvent) {
                // 文件内容变更：增量更新
                handleFileContentChangeEvent(file);
            }
        }
    }

    private void handleFileContentChangeEvent(VirtualFile file) {
        // 等待索引就绪后再访问 PSI，避免 Stub Index 与文件内容不一致
        DumbService.getInstance(project).runWhenSmart(() ->
                ApplicationManager.getApplication().runReadAction(() -> {
                    if (!file.isValid()) {
                        return;
                    }
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                    if (psiFile != null) {
                        cache.updateCacheForFile(psiFile);
                    }
                })
        );
    }
}
