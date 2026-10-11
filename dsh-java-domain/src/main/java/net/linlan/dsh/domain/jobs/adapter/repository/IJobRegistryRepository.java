package net.linlan.dsh.domain.jobs.adapter.repository;

import net.linlan.dsh.domain.jobs.model.entity.JobRecord;
import java.util.List;
import java.util.Optional;

/**
 * IJobRegistryRepository 定义当前模块中的interface契约与职责。
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 */
public interface IJobRegistryRepository {

    void save(JobRecord record);

    Optional<JobRecord> findById(String id);

    List<JobRecord> findByOwnerSessionId(String sessionId);

    List<JobRecord> findAll();

    void deleteById(String id);
}
