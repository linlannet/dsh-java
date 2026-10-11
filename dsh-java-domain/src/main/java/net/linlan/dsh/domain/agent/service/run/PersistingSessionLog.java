package net.linlan.dsh.domain.agent.service.run;

import net.linlan.dsh.domain.session.event.adapter.repository.ISessionEventStore;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 持久化会话日志——在内存 WAL 基础上镜像写入持久化事件存储。
 * <p>
 * ReactLoopAgent 调用 {@code session.append(event)} 写入内存 WAL，
 * 本类覆写 append/appendBatch，在内存追加完成后将事件<strong>异步</strong>镜像到
 * {@link ISessionEventStore}，让事件溯源设施真正运转。
 * <p>
 * 为什么异步：远程 MySQL 单次提交延迟可达数百毫秒，一轮 turn 有 9+ 次串行写库，
 * 同步镜像会把 DB 延迟叠加到流式输出的收尾关键路径上（内容结束后界面仍挂起数秒）。
 * 异步写后内存 WAL 立即返回，落库在单线程执行器中按序执行（保序）；
 * 会话收集（AgentCollectNode）读的是内存 events()，不依赖落库完成。
 * <p>
 * 代价：进程崩溃时可能丢失尚未落库的尾部事件（窗口通常在毫秒级），
 * 对本工具的事件回放场景可接受。
 * <p>
 * 该类位于 infrastructure 层，domain 层的 SessionLog 保持纯净
 * （仅依赖 types SPI，零 Spring 注解）。
 *
 *
 */
public class PersistingSessionLog extends SessionLog {

    private static final Logger log = LoggerFactory.getLogger(PersistingSessionLog.class);

    private final ISessionEventStore store;

    /** 单线程落库执行器：保证同一 session 的事件按 seq 顺序写入 */
    private final ExecutorService writer;

    /**
     * 构造持久化会话日志。
     * <p>
     * 同时将 header 持久化到事件存储，确保会话创建即可回放。
     * header 保持同步写入：它是后续事件落库的前置依赖，且每次会话仅一次。
     *
     * @param sessionId 会话 ID
     * @param header    会话头
     * @param store     持久化事件存储端口
     */
    public PersistingSessionLog(String sessionId, SessionHeader header, ISessionEventStore store) {
        super(sessionId, header);
        this.store = store;
        this.writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "session-wal-writer-" + sessionId.substring(0, Math.min(8, sessionId.length())));
            t.setDaemon(true);
            return t;
        });
        // 持久化会话头，让事件溯源从创建即可回放
        store.saveHeader(header);
    }

    @Override
    public synchronized SessionEvent append(SessionEvent event) {
        SessionEvent appended = super.append(event);
        enqueueWrite(() -> store.append(sessionId(), appended));
        return appended;
    }

    @Override
    public synchronized List<SessionEvent> appendBatch(List<SessionEvent> batch) {
        List<SessionEvent> appended = super.appendBatch(batch);
        if (!appended.isEmpty()) {
            enqueueWrite(() -> store.appendBatch(sessionId(), appended));
        }
        return appended;
    }

    /**
     * 将一次落库任务排入单线程执行器。
     * <p>在 synchronized 块内调用，保证入队顺序与内存 WAL 的 seq 顺序一致。</p>
     */
    private void enqueueWrite(Runnable writeTask) {
        writer.execute(() -> {
            try {
                writeTask.run();
            } catch (Exception e) {
                // 异步落库失败不再打断主流程（内存 WAL 仍完整），仅记录错误
                log.error("[session] 事件异步落库失败 sessionId={}: {}", sessionId(), e.getMessage(), e);
            }
        });
    }
}
