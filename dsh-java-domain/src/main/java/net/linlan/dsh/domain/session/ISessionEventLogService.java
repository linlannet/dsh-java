package net.linlan.dsh.domain.session;

import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import java.util.List;

/**
 * ISessionEventLogService 封装ISessionEventLog会话事件的领域规则与流程。
 */
public interface ISessionEventLogService {

    /**
     * 创建领域对象。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param header header。
     * @return 领域处理结果。
     */
    SessionHeader createSession(SessionHeader header);

    /**
     * 处理append。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    SessionEvent append(String sessionId, SessionEvent event);

    /**
     * 批量追加事件（事件数组重载），按顺序写入并可重放。
     *
     * @param sessionId 会话ID
     * @param events    事件数组
     * @return 已分配序列号的事件列表
     */
    default List<SessionEvent> append(String sessionId, List<SessionEvent> events) {
        return appendBatch(sessionId, events);
    }
    /**
     * 处理appendBatch。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    List<SessionEvent> appendBatch(String sessionId, List<SessionEvent> events);

    /**
     * 处理readAll。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    List<SessionEvent> readAll(String sessionId);

    /**
     * 处理readAfter。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    List<SessionEvent> readAfter(String sessionId, long afterSeq);

    /**
     * 获取Header。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    SessionHeader getHeader(String sessionId);

    /**
     * 获取LastSeq。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    long getLastSeq(String sessionId);

    /**
     * 处理restore。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    void restore(String sessionId);
}
