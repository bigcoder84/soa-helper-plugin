package cn.bigcoder.soa.helper.activity;

import cn.bigcoder.soa.helper.search.RpcMethodCache;
import cn.bigcoder.soa.helper.listener.FileChangeListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RpcMethodSearchPlugin implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super kotlin.Unit> continuation) {
        // 初始化缓存
        RpcMethodCache cache = RpcMethodCache.getInstance(project);
        cache.initialize();

        // 注册文件变化监听器
        new FileChangeListener(project, cache);
        return null;
    }
}
