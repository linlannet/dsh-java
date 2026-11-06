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
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleView;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScheduleService 宿主范围行为")
public class ScheduleServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String RRULE_DAILY = "DTSTART;TZID=UTC:20260101T000000\nRRULE:FREQ=DAILY";

    private static IScheduleService service(Clock clock, IScheduleRepository repo) {
        return new ScheduleService(repo, clock);
    }

    private static ScheduleRecord create(IScheduleService svc, String session, ScheduleTarget target) {
        return svc.create(new ScheduleCreateRequest("标题" + session, "提示词", RRULE_DAILY, target, session));
    }

    @Test
    @DisplayName("this_session 创建时记录 targetSessionId，new_once/new 为空")
    void createTargets() {
        IScheduleRepository repo = new FakeRepo();
        IScheduleService svc = service(Clock.fixed(NOW, ZoneOffset.UTC), repo);
        ScheduleRecord a = create(svc, "sessionA", ScheduleTarget.THIS_SESSION);
        ScheduleRecord b = create(svc, "sessionA", ScheduleTarget.NEW_ONCE);
        assertEquals("sessionA", a.targetSessionId());
        assertNull(b.targetSessionId());
        assertNotNull(a.nextAt());
    }

    @Test
    @DisplayName("宿主范围：任意会话都能查看与列出全部任务")
    void hostWideVisibility() {
        IScheduleRepository repo = new FakeRepo();
        IScheduleService svc = service(Clock.fixed(NOW, ZoneOffset.UTC), repo);
        create(svc, "sessionA", ScheduleTarget.THIS_SESSION);
        create(svc, "sessionB", ScheduleTarget.NEW_ONCE);

        SchedulePage page = svc.list(Boolean.TRUE, 20, 0);
        assertEquals(2, page.total());
        assertEquals(2, page.shown());
        assertEquals(20, page.limit());
        assertEquals(0, page.offset());

        ScheduleView v = page.items().stream()
                .map(ScheduleView::record)
                .filter(r -> r.target() == ScheduleTarget.THIS_SESSION)
                .findFirst()
                .map(r -> svc.view(r.id()).orElseThrow())
                .orElseThrow();
        assertEquals(ScheduleTarget.THIS_SESSION, v.record().target());
    }

    @Test
    @DisplayName("edit update 修改目标并重新计算下次时间")
    void editUpdate() {
        IScheduleRepository repo = new FakeRepo();
        IScheduleService svc = service(Clock.fixed(NOW, ZoneOffset.UTC), repo);
        ScheduleRecord rec = create(svc, "sessionA", ScheduleTarget.THIS_SESSION);
        String id = rec.id();

        ScheduleRecord updated = svc.edit(new ScheduleEditRequest(id, "update",
                new SchedulePatch(null, "新提示词", "DTSTART;TZID=UTC:20260102T000000\nRRULE:FREQ=DAILY",
                        ScheduleTarget.NEW_ONCE, null),
                rec.updatedAt()));
        assertEquals("新提示词", updated.prompt());
        assertEquals(ScheduleTarget.NEW_ONCE, updated.target());
        assertNull(updated.targetSessionId());
        assertEquals(Instant.parse("2026-01-02T00:00:00Z"), updated.nextAt());
    }

    @Test
    @DisplayName("edit 乐观并发冲突")
    void editConflict() {
        IScheduleRepository repo = new FakeRepo();
        IScheduleService svc = service(Clock.fixed(NOW, ZoneOffset.UTC), repo);
        ScheduleRecord rec = create(svc, "sessionA", ScheduleTarget.THIS_SESSION);
        assertThrows(ScheduleConflictException.class, () -> svc.edit(new ScheduleEditRequest(
                rec.id(), "update", new SchedulePatch("x", null, null, null, null),
                Instant.parse("2000-01-01T00:00:00Z"))));
    }

    @Test
    @DisplayName("edit 删除不存在的任务抛错")
    void editDeleteMissing() {
        IScheduleRepository repo = new FakeRepo();
        IScheduleService svc = service(Clock.fixed(NOW, ZoneOffset.UTC), repo);
        assertThrows(ScheduleNotFoundException.class, () -> svc.edit(new ScheduleEditRequest(
                "missing", "delete", null, null)));
    }

    @Test
    @DisplayName("暂停后恢复重新计算 nextAt")
    void pauseThenResume() {
        IScheduleRepository repo = new FakeRepo();
        IScheduleService svc = service(Clock.fixed(NOW, ZoneOffset.UTC), repo);
        ScheduleRecord rec = create(svc, "sessionA", ScheduleTarget.THIS_SESSION);
        ScheduleRecord paused = svc.edit(new ScheduleEditRequest(rec.id(), "update",
                new SchedulePatch(null, null, null, null, Boolean.TRUE), rec.updatedAt()));
        assertTrue(paused.paused());
        assertNull(svc.view(rec.id()).orElseThrow().nextAt());

        ScheduleRecord resumed = svc.edit(new ScheduleEditRequest(paused.id(), "update",
                new SchedulePatch(null, null, null, null, Boolean.FALSE), paused.updatedAt()));
        assertFalse(resumed.paused());
        assertEquals(NOW, svc.view(resumed.id()).orElseThrow().nextAt());
    }

    @Test
    @DisplayName("drive 触发到期任务并推进下一次时间")
    void driveFiresDueTasks() {
        IScheduleRepository repo = new FakeRepo();
        // 用过去起点，使 DAILY 首次发生已 <= now
        IScheduleService svc = service(Clock.fixed(NOW, ZoneOffset.UTC), repo);
        ScheduleRecord rec = create(svc, "sessionA", ScheduleTarget.THIS_SESSION);
        // nextAt == NOW，已到期
        List<ScheduleRecord> fired = svc.drive(NOW);
        assertEquals(1, fired.size());
        assertFalse(fired.get(0).ended());
        assertEquals(Instant.parse("2026-01-02T00:00:00Z"), fired.get(0).nextAt());
    }

    /** 极简内存仓储，用于领域测试。 */
    public static final class FakeRepo implements IScheduleRepository {
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
            return new ArrayList<>(store.values());
        }

        @Override
        public List<ScheduleRecord> findPage(boolean activeOnly, int limit, int offset) {
            return store.values().stream()
                    .filter(r -> !activeOnly || r.active())
                    .skip(Math.max(offset, 0))
                    .limit(Math.max(limit, 0))
                    .toList();
        }

        @Override
        public int count(boolean activeOnly) {
            return (int) store.values().stream().filter(r -> !activeOnly || r.active()).count();
        }

        @Override
        public List<ScheduleRecord> findDue(Instant now) {
            return store.values().stream()
                    .filter(ScheduleRecord::active)
                    .filter(r -> r.nextAt() != null && !r.nextAt().isAfter(now))
                    .toList();
        }
    }
}
