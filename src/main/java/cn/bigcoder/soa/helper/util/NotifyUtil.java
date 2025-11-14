package cn.bigcoder.soa.helper.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;

/**
 * 通知提示工具类
 */
public class NotifyUtil {

    /**
     * 显示错误通知
     *
     * @param project 项目实例
     * @param message 错误消息
     */
    public static void showError(Project project, String message) {
        Notification notification = new Notification("SOA Helper", "SOA 快速跳转", message, NotificationType.ERROR);
        Notifications.Bus.notify(notification, project);
    }

    /**
     * 显示信息通知
     *
     * @param project 项目实例
     * @param message 信息消息
     */
    public static void showInfo(Project project, String message) {
        Notification notification = new Notification("SOA Helper", "SOA 快速跳转", message,
                NotificationType.INFORMATION);
        Notifications.Bus.notify(notification, project);
    }
}
