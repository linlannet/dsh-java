package net.linlan.dsh.infrastructure.adapter.agent;

import net.linlan.dsh.domain.agent.contract.outbound.AgentEventListener;
import net.linlan.dsh.domain.session.event.adapter.repository.ISessionEventStore;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.valobj.SessionEventType;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 持久化 Agent 事件监听器——桥接 Agent 内部事件到持久化事件存储。
 * <p>
 * ReactLoopAgent 通过 {@code events.emit(name, payload)} 派发内部事件
 * （如 agent/cancelled），本监听器将这些事件记录为 Generic SessionEvent
 * 写入 {@link ISessionEventStore}，让事件溯源设施覆盖 Agent 生命周期事件。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>emit 是 fire-and-forget——持久化失败仅记录日志，不影响 Agent 循环</li>
 *   <li>serial 同步等待持久化完成</li>
 *   <li>waterfall 透传 defaultAction，不做拦截</li>
 * </ul>
 *
 *
 */
public class PersistingAgentEventListener implements AgentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PersistingAgentEventListener.class);

    private final String sessionId;
    private final ISessionEventStore store;
    private final ObjectMapper objectMapper;

    /**
     * 处理Persisting Agent 事件 Listener。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @param store store参数。
     * @param objectMapper object Mapper参数。
     * @return 方法执行结果。
     */
    public PersistingAgentEventListener(String sessionId, ISessionEventStore store, ObjectMapper objectMapper) {
        this.sessionId = sessionId;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Override
    public void emit(String name, Object payload) {
        try {
            String json = serializePayload(payload);
            SessionEvent event = new SessionEvent.Generic(
                    store.getLastSeq(sessionId) + 1,
                    java.time.Instant.now(),
                    SessionEventType.AGENT_EVENT,
                    "{\"name\":\"" + name + "\",\"payload\":" + json + "}"
            );
            store.append(sessionId, event);
        } catch (Exception e) {
            log.warn("[AgentEvent] emit 持久化失败 name={} sessionId={}：{}", name, sessionId, e.getMessage());
        }
    }
    /**
     * serial。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param name 名称。
     * @param payload 载荷数据。
     * @return 方法执行结果。
     */


    @Override
    public CompletableFuture<Void> serial(String name, Object payload) {
        try {
            String json = serializePayload(payload);
            SessionEvent event = new SessionEvent.Generic(
                    store.getLastSeq(sessionId) + 1,
                    java.time.Instant.now(),
                    SessionEventType.AGENT_EVENT,
                    "{\"name\":\"" + name + "\",\"payload\":" + json + "}"
            );
            store.append(sessionId, event);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.warn("[AgentEvent] serial 持久化失败 name={} sessionId={}：{}", name, sessionId, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }
    /**
     * waterfall。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param name 名称。
     * @param payload 载荷数据。
     * @param defaultAction default Action参数。
     * @return 方法执行结果。
     */


    @Override
    public <T> CompletableFuture<T> waterfall(String name, Object payload, CompletableFuture<T> defaultAction) {
        // waterfall 是 around-middleware，这里只记录事件，不拦截默认行为
        emit(name, payload);
        return defaultAction;
    }

    /**
     * 处理serialize 载荷。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param payload 载荷数据。
     * @return 转换后的目标对象。
     */
    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return String.valueOf(payload);
        }
    }
}
