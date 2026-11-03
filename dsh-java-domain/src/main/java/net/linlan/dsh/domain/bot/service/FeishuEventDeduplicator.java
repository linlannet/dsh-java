package net.linlan.dsh.domain.bot.service;

import java.util.ArrayDeque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书事件去重器：按 messageId 有界去重，淘汰最旧记录。
 */
public class FeishuEventDeduplicator {

    private static final int MAX_SIZE = 2000;

    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final ArrayDeque<String> order = new ArrayDeque<>();

    /**
     * 判断 messageId 是否重复；
     * 首次出现时入队并返回 false，重复时返回 true；超过容量时淘汰最旧记录。
     *
     * @param messageId 消息 ID
     * @return 重复返回 true
     */
    public synchronized boolean isDuplicate(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        if (!this.seen.add(messageId)) {
            return true;
        }
        this.order.addLast(messageId);
        while (this.order.size() > MAX_SIZE) {
            this.seen.remove(this.order.pollFirst());
        }
        return false;
    }
}
