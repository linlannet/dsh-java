package net.linlan.dsh.infrastructure.dao;

import net.linlan.dsh.infrastructure.dao.po.HarnessTaskPO;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 定义任务的底层持久化访问。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Mapper
public interface IHarnessTaskDao {

    /**
     * 保存一条任务记录。
     *
     * @param taskPO 任务记录
     */
    @Insert("""
            INSERT INTO harness_task (task_id, session_id, prompt, enabled_tools_json, status, submitted_at)
            VALUES (#{taskId}, #{sessionId}, #{prompt}, #{enabledToolsJson}, #{status}, #{submittedAt})
            ON DUPLICATE KEY UPDATE
                session_id = VALUES(session_id),
                prompt = VALUES(prompt),
                enabled_tools_json = VALUES(enabled_tools_json),
                status = VALUES(status),
                submitted_at = VALUES(submitted_at)
            """)
    void save(HarnessTaskPO taskPO);

    /**
     * 按任务 ID 查询一条任务记录。
     *
     * @param taskId 任务 ID
     * @return 命中时返回已存储记录
     */
    @Select("""
            SELECT task_id, session_id, prompt, enabled_tools_json, status, submitted_at
            FROM harness_task
            WHERE task_id = #{taskId}
            """)
    Optional<HarnessTaskPO> findByTaskId(@Param("taskId") String taskId);
}
