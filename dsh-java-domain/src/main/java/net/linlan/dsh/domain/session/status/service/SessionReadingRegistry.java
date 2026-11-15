package net.linlan.dsh.domain.session.status.service;

import net.linlan.dsh.domain.session.status.model.valobj.SessionReadState;
import net.linlan.dsh.domain.session.status.model.valobj.SessionStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * SessionReadingRegistry 维护宿主内存中由所有前端共享的会话阅读记录。
 *
 * <p>调用流程：基线装载 → turn/end 更新完成位置 → mark/acknowledge 推进阅读位置。</p>
 * <p>示例：所有前端离线时结束的轮次仍会在重连后显示为完成未读。</p>
 */
@Service
public class SessionReadingRegistry {

    private final Map<String, SessionReadState> records = new ConcurrentHashMap<>();
    private volatile boolean readingReady;

    /**
     * 装载宿主基线并标记阅读记录可用。
     *
     * @param baseline 初始阅读记录
     */
    public void reportBaseline(Map<String, SessionReadState> baseline) {
        records.clear();
        if (baseline != null) {
            records.putAll(baseline);
        }
        readingReady = true;
    }

    /**
     * 首次查询会话列表时惰性建立宿主基线（已有历史按已读处理）。
     *
     * @param sessionIds 已知会话标识
     */
    public synchronized void ensureBaseline(Collection<String> sessionIds) {
        if (readingReady) {
            return;
        }
        if (sessionIds != null) {
            for (String sessionId : sessionIds) {
                records.putIfAbsent(sessionId, SessionReadState.empty(sessionId));
            }
        }
        readingReady = true;
    }

    /**
     * 阅读记录是否已取得宿主基线。
     *
     * @return true 表示可以执行标记
     */
    public boolean readingReady() {
        return readingReady;
    }

    /**
     * 记录一个完成的轮次。
     *
     * @param sessionId 会话标识
     * @param failed    该轮是否以执行错误结束
     */
    public void recordTurnEnd(String sessionId, boolean failed) {
        records.compute(sessionId, (id, state) -> {
            SessionReadState base = state == null ? SessionReadState.empty(id) : state;
            return base.withCompletion(failed);
        });
    }

    /**
     * 显式标记会话已读/未读。
     *
     * @param sessionId 会话标识
     * @param unread    是否未读
     * @throws IllegalStateException 在取得宿主基线前调用
     */
    public void mark(String sessionId, boolean unread) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (!readingReady) {
            throw new IllegalStateException("reading baseline not ready: " + sessionId);
        }
        records.compute(sessionId, (id, state) -> {
            SessionReadState base = state == null ? SessionReadState.empty(id) : state;
            return unread
                    ? base.withManualUnread(true)
                    : base.withManualUnread(false).advanceRead(base.completedTurn());
        });
    }

    /**
     * 自动确认主视图已读到指定轮次；保留手动未读标记。
     *
     * @param sessionId   会话标识
     * @param throughTurn 已阅读到的轮次
     */
    public void acknowledge(String sessionId, long throughTurn) {
        records.computeIfPresent(sessionId, (id, state) -> state.advanceRead(throughTurn));
    }

    /**
     * 显式读取：清除手动未读标记并推进阅读位置。
     *
     * @param sessionId   会话标识
     * @param throughTurn 已阅读到的轮次
     */
    public void read(String sessionId, long throughTurn) {
        Objects.requireNonNull(sessionId, "sessionId");
        records.compute(sessionId, (id, state) -> {
            SessionReadState base = state == null ? SessionReadState.empty(id) : state;
            return base.withManualUnread(false).advanceRead(throughTurn);
        });
    }

    /**
     * 查询单会话阅读记录。
     *
     * @param sessionId 会话标识
     * @return 阅读记录
     */
    public Optional<SessionReadState> state(String sessionId) {
        return Optional.ofNullable(records.get(sessionId));
    }

    /**
     * 生成单会话状态投影。
     *
     * @param sessionId 会话标识
     * @param running   是否运行中
     * @return 状态投影
     */
    public SessionStatus status(String sessionId, boolean running) {
        return SessionStatus.of(running, records.get(sessionId));
    }

    /**
     * 生成给定会话集合的状态投影。
     *
     * @param sessionIds 会话标识集合
     * @param runningIds 运行中的会话标识
     * @return 按会话标识索引的状态投影
     */
    public Map<String, SessionStatus> snapshot(Collection<String> sessionIds, Collection<String> runningIds) {
        Map<String, SessionStatus> snapshot = new LinkedHashMap<>();
        for (String sessionId : sessionIds) {
            snapshot.put(sessionId, status(sessionId, runningIds.contains(sessionId)));
        }
        return Collections.unmodifiableMap(snapshot);
    }
}
