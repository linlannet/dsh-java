package net.linlan.dsh.infrastructure.dao;

import net.linlan.dsh.infrastructure.dao.po.SessionEventPO;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 定义会话事件的底层追加写访问。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Mapper
public interface ISessionEventDao {

    /**
     * 追加一条事件记录。
     *
     * @param eventPO 事件记录
     */
    @Insert("""
            INSERT INTO harness_session_event (session_id, event_type, payload, occurred_at)
            VALUES (#{sessionId}, #{eventType}, #{payload}, #{occurredAt})
            """)
    void append(SessionEventPO eventPO);

    /**
     * 查询某会话的全部事件记录。
     *
     * @param sessionId 会话 ID
     * @return 按顺序排列的事件记录
     */
    @Select("""
            SELECT session_id, event_type, payload, occurred_at
            FROM harness_session_event
            WHERE session_id = #{sessionId}
            ORDER BY occurred_at ASC, id ASC
            """)
    List<SessionEventPO> queryBySessionId(@Param("sessionId") String sessionId);
}
