package net.linlan.dsh.infrastructure.adapter.plugin.java;

import net.linlan.dsh.domain.spi.PluginEvent;
import net.linlan.dsh.domain.spi.PluginEventBus;
import net.linlan.dsh.domain.spi.PluginEventHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 进程内插件事件发布/订阅总线。
 * <p>订阅表按事件名保存，底层使用 CopyOnWriteArrayList，注册/遍历时线程安全。
 * 事件同步分发；单个处理器异常只记录告警并被隔离，不影响发布方和后续处理器。
 * <p>数据示例：事件 "greeting" 有 a、b 两个订阅者，a 抛异常，b 仍会收到事件。
 */
@Component
public final class InProcessPluginEventBus implements PluginEventBus {

    private static final Logger log = LoggerFactory.getLogger(InProcessPluginEventBus.class);

    private final Map<String, List<Subscriber>> subscribers = new ConcurrentHashMap<>();

    /**
     * 按事件名同步发布事件。
     * <p>没有订阅者时直接返回；有订阅者时复制当前列表，防止遍历期间修改。
     */
    @Override
    public void publish(PluginEvent event) {
        Objects.requireNonNull(event, "event");
        List<Subscriber> handlers = subscribers.get(event.name());
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        for (Subscriber subscriber : List.copyOf(handlers)) {
            try {
                subscriber.handler().handle(event);
            } catch (Exception e) {
                log.warn("Plugin event handler '{}' for '{}' threw exception: {}",
                        subscriber.pluginId(), event.name(), e.getMessage(), e);
            }
        }
    }

    /**
     * 注册一个事件订阅者并返回取消订阅器。
     * <p>取消最后一个订阅者时会移除空事件名，避免 subscribers 无限增长。
     */
    @Override
    public AutoCloseable subscribe(String eventName, String pluginId, PluginEventHandler handler) {
        Objects.requireNonNull(eventName, "eventName");
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(handler, "handler");
        Subscriber subscriber = new Subscriber(pluginId, handler);
        subscribers.computeIfAbsent(eventName, key -> new CopyOnWriteArrayList<>()).add(subscriber);
        return () -> {
            List<Subscriber> list = subscribers.get(eventName);
            if (list != null) {
                list.remove(subscriber);
                if (list.isEmpty()) {
                    subscribers.remove(eventName, list);
                }
            }
        };
    }

    /**
     * 返回指定事件名当前的订阅者数量。
     */
    @Override
    public int subscriberCount(String eventName) {
        List<Subscriber> list = subscribers.get(eventName);
        return list == null ? 0 : list.size();
    }

    /**
     * 移除指定插件的所有事件订阅。
     */
    @Override
    public void unsubscribeAll(String pluginId) {
        subscribers.values().forEach(list ->
                list.removeIf(subscriber -> subscriber.pluginId().equals(pluginId)));
    }

    /**
     * 返回当前仍有订阅者的事件名称快照。
     */
    @Override
    public List<String> eventNames() {
        return new ArrayList<>(subscribers.keySet());
    }

    /**
     * 一个事件订阅者：来源插件 ID 和处理器。
     */
    private record Subscriber(String pluginId, PluginEventHandler handler) {}
}
