package net.linlan.dsh.infrastructure.dao;

import net.linlan.dsh.infrastructure.dao.po.SessionEventRowPO;
import net.linlan.dsh.infrastructure.dao.po.SessionHeaderRowPO;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 新会话事件存储的 MyBatis DAO。
 * 按序列号保存类型化事件。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Mapper
public interface ISessionEventRowDao {

    /**
     * 会话事件表不存在时创建。
     * Standalone 模式启动时调用。
     */
    @Insert("""
            CREATE TABLE IF NOT EXISTS harness_session_event_log (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                session_id VARCHAR(64) NOT NULL,
                seq BIGINT NOT NULL,
                event_type VARCHAR(64) NOT NULL,
                event_time TIMESTAMP NOT NULL,
                payload_json TEXT,
                surface_op VARCHAR(16),
                source_event_seqs VARCHAR(512),
                UNIQUE (session_id, seq)
            )
            """)
    void createTableIfNotExists();

    /**
     * 插入一条事件记录。
     */
    @Insert("""
            INSERT INTO harness_session_event_log
                (session_id, seq, event_type, event_time, payload_json, surface_op, source_event_seqs)
            VALUES
                (#{sessionId}, #{seq}, #{eventType}, #{eventTime}, #{payloadJson}, #{surfaceOp}, #{sourceEventSeqs})
            """)
    void insert(SessionEventRowPO row);

    /**
     * 批量插入事件记录。
     */
    @Insert("""
            <script>
            INSERT INTO harness_session_event_log
                (session_id, seq, event_type, event_time, payload_json, surface_op, source_event_seqs)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.sessionId}, #{row.seq}, #{row.eventType}, #{row.eventTime}, #{row.payloadJson}, #{row.surfaceOp}, #{row.sourceEventSeqs})
            </foreach>
            </script>
            """)
    void insertBatch(@Param("rows") List<SessionEventRowPO> rows);

    /**
     * 读取某会话全部事件，并按序列号排序。
     */
    @Select("""
            SELECT id, session_id, seq, event_type, event_time, payload_json, surface_op, source_event_seqs
            FROM harness_session_event_log
            WHERE session_id = #{sessionId}
            ORDER BY seq ASC
            """)
    List<SessionEventRowPO> queryBySessionId(@Param("sessionId") String sessionId);

    /**
     * 读取指定序列号之后的事件。
     */
    @Select("""
            SELECT id, session_id, seq, event_type, event_time, payload_json, surface_op, source_event_seqs
            FROM harness_session_event_log
            WHERE session_id = #{sessionId} AND seq > #{afterSeq}
            ORDER BY seq ASC
            """)
    List<SessionEventRowPO> queryAfter(@Param("sessionId") String sessionId, @Param("afterSeq") long afterSeq);

    /**
     * 读取 [fromSeq, toSeq] 区间内的事件。
     */
    @Select("""
            SELECT id, session_id, seq, event_type, event_time, payload_json, surface_op, source_event_seqs
            FROM harness_session_event_log
            WHERE session_id = #{sessionId} AND seq >= #{fromSeq} AND seq <= #{toSeq}
            ORDER BY seq ASC
            """)
    List<SessionEventRowPO> queryRange(@Param("sessionId") String sessionId,
                                        @Param("fromSeq") long fromSeq,
                                        @Param("toSeq") long toSeq);

    @Select("""
            SELECT COALESCE(MAX(seq), -1) FROM harness_session_event_log WHERE session_id = #{sessionId}
            """)
    Long getMaxSeq(@Param("sessionId") String sessionId);

    /**
     * 统计某会话的事件数量。
     */
    @Select("""
            SELECT COUNT(*) FROM harness_session_event_log WHERE session_id = #{sessionId}
            """)
    long count(@Param("sessionId") String sessionId);

    /**
     * 保存或更新会话头。
     */
    @Insert("""
            INSERT INTO harness_session_header
                (session_id, version, created_at, cwd, parent_session, seed_length, origin, delegation_depth, agent_preset)
            VALUES
                (#{sessionId}, #{version}, #{createdAt}, #{cwd}, #{parentSession}, #{seedLength}, #{origin}, #{delegationDepth}, #{agentPreset})
            ON DUPLICATE KEY UPDATE version = #{version}
            """)
    void upsertHeader(@Param("sessionId") String sessionId,
                      @Param("version") int version,
                      @Param("createdAt") java.time.Instant createdAt,
                      @Param("cwd") String cwd,
                      @Param("parentSession") String parentSession,
                      @Param("seedLength") int seedLength,
                      @Param("origin") String origin,
                      @Param("delegationDepth") int delegationDepth,
                      @Param("agentPreset") String agentPreset);

    @Select("""
            SELECT session_id, version, created_at, cwd, parent_session, seed_length, origin, delegation_depth, agent_preset
            FROM harness_session_header
            WHERE session_id = #{sessionId}
            """)
    SessionHeaderRowPO getHeader(@Param("sessionId") String sessionId);

    @Select("""
            SELECT session_id, version, created_at, cwd, parent_session, seed_length, origin, delegation_depth, agent_preset
            FROM harness_session_header
            ORDER BY created_at DESC
            LIMIT #{limit}
            """)
    List<SessionHeaderRowPO> listHeaders(@Param("limit") int limit);
}
