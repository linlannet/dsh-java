package net.linlan.dsh.domain.schedule.service;

import net.linlan.dsh.domain.schedule.IScheduleService;
import net.linlan.dsh.domain.schedule.adapter.repository.IScheduleRepository;
import net.linlan.dsh.domain.schedule.model.entity.ScheduleRecord;
import net.linlan.dsh.domain.schedule.model.exception.ScheduleConflictException;
import net.linlan.dsh.domain.schedule.model.exception.ScheduleNotFoundException;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleCreateRequest;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleEditRequest;
import net.linlan.dsh.domain.schedule.model.valobj.SchedulePage;
import net.linlan.dsh.domain.schedule.model.valobj.SchedulePatch;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleTarget;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleTiming;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleView;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 宿主范围计划任务服务。
 *
 * <p>职责：按 id 创建、查看、分页列出、编辑与删除宿主的全部计划任务，并驱动到期任务。</p>
 * <p>流程：创建（RRULE + target）→ 到期扫描 → 计算下一次发生时间 → 记录触发结果。</p>
 * <p>示例：{@code DTSTART;TZID=Asia/Shanghai:20261013T090000\nRRULE:FREQ=DAILY} 每日 09:00 投递到调用方会话。</p>
 */
@Service
public class ScheduleService implements IScheduleService {

    /** 默认分页大小。 */
    private static final int DEFAULT_LIMIT = 20;
    /** 最大分页大小。 */
    private static final int MAX_LIMIT = 100;

    private final IScheduleRepository repository;
    private final Clock clock;

    /**
     * 构造计划任务服务（系统时钟）。
     *
     * @param repository 任务仓储
     */
    public ScheduleService(IScheduleRepository repository) {
        this(repository, Clock.systemUTC());
    }

    /**
     * 构造计划任务服务。
     *
     * @param repository 任务仓储
     * @param clock 时钟，便于测试注入
     */
    public ScheduleService(IScheduleRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public ScheduleRecord create(ScheduleCreateRequest request) {
        Instant now = clock.instant();
        ScheduleTiming timing = ScheduleTiming.rrule(request.rrule(), now);
        String targetSessionId = request.target() == ScheduleTarget.THIS_SESSION
                ? request.sourceSessionId()
                : null;
        ScheduleRecord record = new ScheduleRecord(
                allocateId(),
                request.title().trim(),
                request.prompt().trim(),
                timing,
                request.target(),
                targetSessionId,
                request.sourceSessionId(),
                false,
                false,
                now,
                now,
                null,
                timing.firstAtOrAfter(now));
        repository.save(record);
        return record;
    }

    @Override
    public Optional<ScheduleView> view(String id) {
        Instant now = clock.instant();
        return repository.findById(id).map(record -> ScheduleView.of(record, now));
    }

    @Override
    public SchedulePage list(Boolean active, int limit, int offset) {
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        int effectiveOffset = Math.max(offset, 0);
        boolean activeOnly = Boolean.TRUE.equals(active);
        Instant now = clock.instant();
        List<ScheduleView> items = repository.findPage(activeOnly, effectiveLimit, effectiveOffset).stream()
                .map(record -> ScheduleView.of(record, now))
                .toList();
        return SchedulePage.of(items, repository.count(activeOnly), effectiveLimit, effectiveOffset);
    }

    @Override
    public ScheduleRecord edit(ScheduleEditRequest request) {
        Instant now = clock.instant();
        ScheduleRecord existing = repository.findById(request.id())
                .orElseThrow(() -> new ScheduleNotFoundException(request.id()));
        if (request.expectedUpdatedAt() != null
                && !request.expectedUpdatedAt().equals(existing.updatedAt())) {
            throw new ScheduleConflictException(request.id(), request.expectedUpdatedAt(), existing.updatedAt());
        }
        if (request.deleteAction()) {
            repository.deleteById(request.id());
            return new ScheduleRecord(existing.id(), existing.title(), existing.prompt(), existing.timing(),
                    existing.target(), existing.targetSessionId(), existing.sourceSessionId(),
                    existing.paused(), true, existing.createdAt(), now, existing.lastRunAt(), null);
        }

        SchedulePatch patch = request.patch();
        String title = patch.title() == null ? null : requireText(patch.title(), "title");
        String prompt = patch.prompt() == null ? null : requireText(patch.prompt(), "prompt");

        ScheduleTiming timing = existing.timing();
        Instant nextAt = existing.nextAt();
        if (patch.rrule() != null) {
            timing = ScheduleTiming.rrule(patch.rrule(), now);
            nextAt = timing.firstAtOrAfter(now);
        }
        ScheduleTarget target = patch.target() != null ? patch.target() : existing.target();
        String targetSessionId = target == ScheduleTarget.THIS_SESSION
                ? existing.sourceSessionId()
                : null;
        if (Boolean.TRUE.equals(patch.paused())) {
            nextAt = null;
        } else if (Boolean.FALSE.equals(patch.paused())) {
            nextAt = timing.firstAtOrAfter(now);
        }
        ScheduleRecord updated = existing.patched(
                title, prompt, patch.rrule() != null ? timing : null, patch.target(), targetSessionId,
                patch.paused(), nextAt, now);
        repository.save(updated);
        return updated;
    }

    @Override
    public List<ScheduleRecord> drive(Instant now) {
        List<ScheduleRecord> due = repository.findDue(now);
        if (due.isEmpty()) {
            return List.of();
        }
        List<ScheduleRecord> fired = new ArrayList<>(due.size());
        for (ScheduleRecord record : due) {
            Instant scheduledFor = record.nextAt();
            Instant next = record.timing().nextAfter(now);
            ScheduleRecord updated = record.fired(scheduledFor, next, now);
            repository.save(updated);
            fired.add(updated);
        }
        return List.copyOf(fired);
    }

    private static String requireText(String value, String field) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    /**
     * 生成宿主内唯一的任务 ID。
     *
     * @return 任务 ID
     */
    private static String allocateId() {
        return "sch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
