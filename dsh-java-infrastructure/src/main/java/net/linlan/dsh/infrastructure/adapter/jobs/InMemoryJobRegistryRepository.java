package net.linlan.dsh.infrastructure.adapter.jobs;

import net.linlan.dsh.domain.jobs.adapter.repository.IJobRegistryRepository;
import net.linlan.dsh.domain.jobs.model.entity.JobRecord;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory job registry repository for standalone mode.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class InMemoryJobRegistryRepository implements IJobRegistryRepository {

    private final ConcurrentHashMap<String, JobRecord> store = new ConcurrentHashMap<>();

    @Override
    public void save(JobRecord record) {
        store.put(record.id(), record);
    }

    @Override
    public Optional<JobRecord> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
    /**
     * 查找By Owner Session Id。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */


    @Override
    public List<JobRecord> findByOwnerSessionId(String sessionId) {
        return store.values().stream()
                .filter(r -> sessionId.equals(r.ownerSessionId()))
                .toList();
    }
    /**
     * 查找All。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public List<JobRecord> findAll() {
        return List.copyOf(store.values());
    }
    /**
     * 删除By Id。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param id id参数。
     */


    @Override
    public void deleteById(String id) {
        store.remove(id);
    }
}
