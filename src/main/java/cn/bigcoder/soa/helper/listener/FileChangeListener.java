package cn.bigcoder.soa.helper.listener;

import cn.bigcoder.soa.helper.search.RpcMethodCache;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FileChangeListener implements BulkFileListener {

    private final Project project;
    private final RpcMethodCache cache;
    private final Alarm debounceAlarm;
    // 防抖延迟时间（毫秒）
    private static final int DEBOUNCE_DELAY_MS = 2000;

    public FileChangeListener(Project project, RpcMethodCache cache) {
        this.project = project;
        this.cache = cache;
        this.debounceAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
        // 通过全局消息总线订阅
        project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, this);
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        // 只处理java文件变更
        events = events.stream()
                .filter(event -> event.getFile() != null && event.getFile().getName().endsWith(".java")).toList();
        
        if (events.isEmpty()) {
            return;
        }
        
        // 判断是否重新扫描整个项目
        if (checkFullScan(events)) {
            // 取消之前的扫描任务
            debounceAlarm.cancelAllRequests();
            // 延迟执行扫描，防抖
            debounceAlarm.addRequest(() -> cache.asyncScanRpcMethods(), DEBOUNCE_DELAY_MS);
        } else {
            // 单独的文件变更事件
            for (VFileEvent event : events) {
                handleFileContentChangeEvent(event);
            }
        }
    }

    /**
     * 校验此次变更是否需要全局扫描
     *
     * @param events
     * @return
     */
    private boolean checkFullScan(List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
            // 文件移动或者删除事件
            if (event instanceof VFileDeleteEvent || event instanceof VFileMoveEvent) {
                return true;
            }
        }
        // 提高阈值，从 10 提高到 50，减少不必要的全量扫描
        return events.size() >= 50;
    }

    /**
     * 处理文件内容变更事件
     *
     * @param event
     */
    private void handleFileContentChangeEvent(VFileEvent event) {
        VirtualFile file = event.getFile();
        if (file == null) {
            return;
        }
        if (event instanceof VFileContentChangeEvent) {
            // 文件内容变化，更新该文件相关的缓存
            PsiManager psiManager = PsiManager.getInstance(project);
            ApplicationManager.getApplication().executeOnPooledThread(() ->
                    ApplicationManager.getApplication().runReadAction(() -> {
                        PsiFile psiFile = psiManager.findFile(file);
                        if (psiFile != null) {
                            cache.updateCacheForFile(psiFile);
                        }
                    })
            );
        }
    }
}