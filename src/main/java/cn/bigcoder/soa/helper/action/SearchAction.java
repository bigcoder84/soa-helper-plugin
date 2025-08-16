package cn.bigcoder.soa.helper.action;

import cn.bigcoder.soa.helper.util.NavigationUtil;
import cn.bigcoder.soa.helper.search.RpcMethodCache;
import cn.bigcoder.soa.helper.search.RpcMethodInfo;
import cn.bigcoder.soa.helper.ui.RpcMethodSearchDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;

// 搜索动作类
public class SearchAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        if (project == null) {
            return;
        }

        // 显示搜索对话框
        RpcMethodCache cache = RpcMethodCache.getInstance(project);
        RpcMethodSearchDialog dialog = new RpcMethodSearchDialog(project, cache);
        dialog.show();

        // 处理选择结果
        if (dialog.isOK()) {
            RpcMethodInfo selectedMethod = dialog.getSelectedMethod();
            if (selectedMethod != null) {
                NavigationUtil.navigateToMethod(project, selectedMethod.filePath(), selectedMethod.textOffset());
            }
        }
    }
}