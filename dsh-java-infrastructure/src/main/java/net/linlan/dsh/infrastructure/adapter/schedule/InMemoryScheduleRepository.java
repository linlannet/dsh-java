package net.linlan.dsh.infrastructure.adapter.schedule;

import net.linlan.dsh.domain.schedule.adapter.repository.IScheduleRepository;
import net.linlan.dsh.domain.schedule.model.entity.ScheduleRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standalone 模式的内存调度仓库。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class InMemoryScheduleRepository implements IScheduleRepository {

    private final ConcurrentHashMap<String, ScheduleRecord> store = new ConcurrentHashMap<>();

    @Override
    public void save(ScheduleRecord record) {
        store.put(record.id(), record);
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }
    /**
     * 查找By Id。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param id id参数。
     * @return 方法执行结果。
     */


    @Override
    public Optional<ScheduleRecord> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
    /**
     * 查找By Session Id。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */


    @Override
    public List<ScheduleRecord> findBySessionId(String sessionId) {
        return store.values().stream()
                .filter(r -> r.sessionId().equals(sessionId))
                .toList();
    }
    /**
     * 查找Due。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @param now now参数。
     * @return 方法执行结果。
     */


    @Override
    public List<ScheduleRecord> findDue(String sessionId, Instant now) {
        return store.values().stream()
                .filter(r -> r.sessionId().equals(sessionId))
                .filter(r -> !r.scheduledAt().isAfter(now))
                .toList();
    }
}
