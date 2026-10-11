package net.linlan.dsh.infrastructure.dao;

import net.linlan.dsh.infrastructure.dao.po.HarnessSessionPO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 定义会话的底层持久化访问。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Mapper
public interface IHarnessSessionDao {

    /**
     * 保存一条会话记录。
     *
     * @param sessionPO 会话记录
     */
    @Insert("""
            INSERT INTO harness_session (session_id, workspace_id, profile_code, execution_mode, channel_code, resolved_model, status, created_at)
            VALUES (#{sessionId}, #{workspaceId}, #{profileCode}, #{executionMode}, #{resolvedChannelCode}, #{resolvedModel}, #{status}, #{createdAt})
            ON DUPLICATE KEY UPDATE
                workspace_id = VALUES(workspace_id),
                profile_code = VALUES(profile_code),
                execution_mode = VALUES(execution_mode),
                channel_code = VALUES(channel_code),
                resolved_model = VALUES(resolved_model),
                status = VALUES(status),
                created_at = VALUES(created_at)
            """)
    void save(HarnessSessionPO sessionPO);

    /**
     * 按会话 ID 查询一条会话记录。
     *
     * @param sessionId 会话 ID
     * @return 命中时返回已存储记录
     */
    @Select("""
            SELECT session_id, workspace_id, profile_code, execution_mode, channel_code, resolved_model, status, created_at
            FROM harness_session
            WHERE session_id = #{sessionId}
            """)
    Optional<HarnessSessionPO> findBySessionId(@Param("sessionId") String sessionId);

    @Select("""
            SELECT session_id, workspace_id, profile_code, execution_mode, channel_code, resolved_model, status, created_at
            FROM harness_session
            ORDER BY created_at DESC
            LIMIT #{limit}
            """)
    List<HarnessSessionPO> listRecent(@Param("limit") int limit);
}
