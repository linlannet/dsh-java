package net.linlan.dsh.domain.schedule.service;

import net.linlan.dsh.domain.schedule.IScheduleService;
import net.linlan.dsh.domain.schedule.adapter.repository.IScheduleRepository;
import net.linlan.dsh.domain.schedule.model.entity.ScheduleRecord;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleKind;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleView;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 会话级持久化提醒服务。
 * <p>职责：创建延时、定时和周期提醒；查询活跃提醒；按 ID 删除；驱动到期任务。</p>
 * <p>流程：创建计划 → 到期扫描 → 执行任务 → 更新状态。</p>
 * <p>示例：设置 300 秒周期提醒后，调度器到期触发任务并记录结果。</p>
 */
@Service
public class ScheduleService implements IScheduleService {

    private static final long MIN_EVERY_INTERVAL_SECONDS = 300;

    private final IScheduleRepository repository;

    /**
     * 构造调度服务。
     *
     * @param repository 调度记录仓储。
     */
    public ScheduleService(IScheduleRepository repository) {
        this.repository = repository;
    }

    @Override
    public ScheduleRecord createAfter(String sessionId, String prompt, Duration delay) {
        if (delay.isNegative() || delay.isZero()) {
            throw new IllegalArgumentException("after delay must be positive");
        }
        Instant target = Instant.now().plus(delay);
        ScheduleRecord record = new ScheduleRecord(
                allocateId(), ScheduleKind.AFTER, prompt.trim(), delay.getSeconds(), null, target, sessionId);
        repository.save(record);
        return record;
    }
    /**
     * 创建一次性定时提醒。
     *
     * @param sessionId 会话 ID。
     * @param prompt 提醒内容。
     * @param target 目标触发时间。
     * @return 调度记录。
     */


    @Override
    public ScheduleRecord createAt(String sessionId, String prompt, Instant target) {
        if (target.isBefore(Instant.now())) {
            throw new IllegalArgumentException("at target must be in the future");
        }
        ScheduleRecord record = new ScheduleRecord(
                allocateId(), ScheduleKind.AT, prompt.trim(), null, null, target, sessionId);
        repository.save(record);
        return record;
    }
    /**
     * 创建固定周期提醒。
     *
     * @param sessionId 会话 ID。
     * @param prompt 提醒内容。
     * @param interval 触发间隔。
     * @return 调度记录。
     */


    @Override
    public ScheduleRecord createEvery(String sessionId, String prompt, Duration interval) {
        if (interval.getSeconds() < MIN_EVERY_INTERVAL_SECONDS) {
            throw new IllegalArgumentException(
                    "every interval must be >= " + MIN_EVERY_INTERVAL_SECONDS + " seconds");
        }
        Instant target = Instant.now().plus(interval);
        ScheduleRecord record = new ScheduleRecord(
                allocateId(), ScheduleKind.EVERY, prompt.trim(), null, interval.getSeconds(), target, sessionId);
        repository.save(record);
        return record;
    }

    @Override
    public List<ScheduleView> list(String sessionId) {
        Instant now = Instant.now();
        return repository.findBySessionId(sessionId).stream()
                .map(r -> ScheduleView.of(r, now))
                .toList();
    }
    /**
     * 删除目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @param id id参数。
     * @return 方法执行结果。
     */


    @Override
    public boolean delete(String sessionId, String id) {
        return repository.findById(id)
                .filter(r -> r.sessionId().equals(sessionId))
                .map(r -> { repository.deleteById(id); return true; })
                .orElse(false);
    }
    /**
     * 驱动到期提醒并更新一次性任务状态。
     *
     * @param sessionId 会话 ID。
     * @param now 当前时间。
     * @return 到期提醒记录。
     */


    @Override
    public List<ScheduleRecord> drive(String sessionId, Instant now) {
        List<ScheduleRecord> due = repository.findDue(sessionId, now);
        for (ScheduleRecord record : due) {
            if (record.kind() == ScheduleKind.EVERY) {
                // Advance to next anchor-aligned occurrence past now
                long interval = record.everySeconds();
                Instant next = record.scheduledAt();
                while (!next.isAfter(now)) {
                    next = next.plusSeconds(interval);
                }
                ScheduleRecord advanced = new ScheduleRecord(
                        record.id(), record.kind(), record.prompt(),
                        record.afterSeconds(), record.everySeconds(), next, record.sessionId());
                repository.save(advanced);
            } else {
                // One-shot: mark as dispatched by deleting
                repository.deleteById(record.id());
            }
        }
        return due;
    }

    /**
     * 生成会话内唯一的调度记录 ID。
     *
     * @return 调度记录 ID。
     */
    private static String allocateId() {
        return "sch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
