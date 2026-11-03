package net.linlan.dsh.infrastructure.adapter.schedule;

import net.linlan.dsh.domain.schedule.adapter.repository.IScheduleRepository;
import net.linlan.dsh.domain.schedule.model.entity.ScheduleRecord;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standalone 模式的宿主范围内存调度仓库。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class InMemoryScheduleRepository implements IScheduleRepository {

    private static final Comparator<ScheduleRecord> BY_CREATED =
            Comparator.comparing(ScheduleRecord::createdAt).thenComparing(ScheduleRecord::id);

    private final Map<String, ScheduleRecord> store = new ConcurrentHashMap<>();

    @Override
    public void save(ScheduleRecord record) {
        store.put(record.id(), record);
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }

    @Override
    public Optional<ScheduleRecord> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<ScheduleRecord> findAll() {
        return store.values().stream().sorted(BY_CREATED).toList();
    }

    @Override
    public List<ScheduleRecord> findPage(boolean activeOnly, int limit, int offset) {
        return store.values().stream()
                .filter(record -> !activeOnly || record.active())
                .sorted(BY_CREATED)
                .skip(Math.max(offset, 0))
                .limit(Math.max(limit, 0))
                .toList();
    }

    @Override
    public int count(boolean activeOnly) {
        return (int) store.values().stream()
                .filter(record -> !activeOnly || record.active())
                .count();
    }

    @Override
    public List<ScheduleRecord> findDue(Instant now) {
        return store.values().stream()
                .filter(ScheduleRecord::active)
                .filter(record -> record.nextAt() != null && !record.nextAt().isAfter(now))
                .sorted(Comparator.comparing(ScheduleRecord::nextAt))
                .toList();
    }
}
