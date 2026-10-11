package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.shared.model.valobj.HarnessStatusEnumVO;
import net.linlan.dsh.domain.task.queue.adapter.repository.IHarnessTaskQueueRepository;
import net.linlan.dsh.domain.task.queue.model.entity.HarnessTaskEntity;
import net.linlan.dsh.infrastructure.dao.IHarnessTaskDao;
import net.linlan.dsh.infrastructure.dao.po.HarnessTaskPO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通过 DAO 和缓存层持久化任务。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Repository
public class HarnessTaskRepository implements IHarnessTaskQueueRepository {

    private final IHarnessTaskDao harnessTaskDao;
    private final ObjectMapper objectMapper;

    /**
     * 处理Harness 任务 Repository。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param harnessTaskDao harness 任务 Dao参数。
     * @param objectMapper object Mapper参数。
     * @return 方法执行结果。
     */
    public HarnessTaskRepository(IHarnessTaskDao harnessTaskDao, ObjectMapper objectMapper) {
        this.harnessTaskDao = harnessTaskDao;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void save(HarnessTaskEntity task) {
        HarnessTaskPO taskPO = new HarnessTaskPO(
                task.getTaskId(),
                task.getSessionId(),
                task.getPrompt(),
                toJson(task.getEnabledTools()),
                task.getStatus().name(),
                task.getSubmittedAt()
        );
        harnessTaskDao.save(taskPO);
    }
    /**
     * 查找By Task Id。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param taskId 任务 ID。
     * @return 方法执行结果。
     */


    @Override
    public Optional<HarnessTaskEntity> findByTaskId(String taskId) {
        return harnessTaskDao.findByTaskId(taskId).map(taskPO -> new HarnessTaskEntity(
                taskPO.taskId(),
                taskPO.sessionId(),
                taskPO.prompt(),
                fromJson(taskPO.enabledToolsJson()),
                HarnessStatusEnumVO.valueOf(taskPO.status()),
                taskPO.submittedAt()
        ));
    }

    /**
     * 转换为 JSON。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param enabledTools enabled Tools参数。
     * @return 转换后的目标对象。
     */
    private String toJson(List<String> enabledTools) {
        try {
            return objectMapper.writeValueAsString(enabledTools);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize enabled tools", exception);
        }
    }

    /**
     * 根据 JSON。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param enabledToolsJson enabled Tools JSON参数。
     * @return 创建的领域对象。
     */
    private List<String> fromJson(String enabledToolsJson) {
        try {
            return objectMapper.readValue(enabledToolsJson, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to deserialize enabled tools", exception);
        }
    }
}
