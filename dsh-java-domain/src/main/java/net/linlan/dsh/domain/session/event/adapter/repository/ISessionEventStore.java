package net.linlan.dsh.domain.session.event.adapter.repository;

import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import java.util.List;
import java.util.Optional;

/**
 * ISessionEventStore 表示ISessionEventStore会话事件。
 */
public interface ISessionEventStore {

    /**
     * 处理saveHeader。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param header header。
     * @return 领域处理结果。
     */
    void saveHeader(SessionHeader header);

    /**
     * 获取Header。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    Optional<SessionHeader> getHeader(String sessionId);

    /**
     * 处理append。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    void append(String sessionId, SessionEvent event);

    /**
     * 处理appendBatch。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    void appendBatch(String sessionId, List<SessionEvent> events);

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
     * 处理readRange。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @param toSeq toSeq。
     * @return 领域处理结果。
     */
    List<SessionEvent> readRange(String sessionId, long fromSeq, long toSeq);

    /**
     * 获取LastSeq。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    long getLastSeq(String sessionId);

    /**
     * 处理count。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    long count(String sessionId);

    /**
     * 处理listHeadersHeader列表。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param limit limit。
     * @return 领域处理结果。
     */
    List<SessionHeader> listHeaders(int limit);
}
