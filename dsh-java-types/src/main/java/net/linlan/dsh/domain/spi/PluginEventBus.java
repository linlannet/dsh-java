package net.linlan.dsh.domain.spi;

import java.util.List;

/**
 * 插件内核的事件发布/订阅总线。
 * <p>默认语义是“发送后即忘”：事件按订阅顺序同步分发，但单个监听器异常会被隔离，
 * 不会中断发布方，也不会阻断后续监听器。
 * <p>数据示例：插件 A 订阅 "session.start"，插件 B 订阅并抛异常，则 B 记录告警，A 仍会收到事件。
 */
public interface PluginEventBus {

    /**
     * 向所有匹配订阅者发布事件。
     */
    void publish(PluginEvent event);

    /**
     * 按事件名注册处理器，返回可用于取消订阅的关闭器。
     */
    AutoCloseable subscribe(String eventName, String pluginId, PluginEventHandler handler);

    /**
     * 返回指定事件名当前的订阅者数量。
     */
    int subscriberCount(String eventName);

    /**
     * 移除指定插件 ID 的全部事件订阅。
     */
    void unsubscribeAll(String pluginId);

    /**
     * 返回当前仍存在订阅者的事件名称，用于诊断。
     */
    List<String> eventNames();
}
