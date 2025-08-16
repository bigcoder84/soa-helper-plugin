package cn.bigcoder.soa.helper.search;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "RpcMethodHistory",
        storages = @Storage("RpcMethodHistory.xml")
)
public class RpcMethodHistoryManager implements PersistentStateComponent<RpcMethodHistoryManager> {

    // Set序列化成String时，可以用@XCollection注解
    @XCollection(style = XCollection.Style.v2, propertyElementName = "histories", elementTypes = {RpcMethodHistoryInfo.class})
    private final List<RpcMethodHistoryInfo> histories = new ArrayList<>();

    private static final int MAX_HISTORY_SIZE = 100;


    public static RpcMethodHistoryManager getInstance(Project project) {
        // 使用新的方式获取项目级服务实例
        return project.getService(RpcMethodHistoryManager.class);
    }

    public List<RpcMethodHistoryInfo> getHistories() {
        return histories;
    }

    public void addToHistory(RpcMethodHistoryInfo entry) {
        // 若历史记录中存在该条目，则先移除
        histories.remove(entry);
        // 将条目添加到历史记录的最前面
        histories.add(0, entry);
        // 限制历史记录数量为 10 条
        while (histories.size() > MAX_HISTORY_SIZE) {
            histories.remove(histories.size() - 1);
        }
    }

    @Nullable
    @Override
    public RpcMethodHistoryManager getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull RpcMethodHistoryManager state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
