package net.linlan.dsh.domain.session.event.service.migration;

import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.model.valobj.SessionFormatVersion;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MigrationCatalog 迁移链集成")
class MigrationCatalogTest {

    @Test
    @DisplayName("defaultChain 包含 4 步迁移")
    void defaultChainHasFourSteps() {
        var catalog = MigrationCatalog.defaultChain();
        assertEquals(4, catalog.chainSize());
    }

    @Test
    @DisplayName("加载 V2 流依次执行 v2→v3→v4 迁移，输出 formatVersion==4")
    void migrateV2ToCurrent() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSession(2);
        session.append(SessionEventFactory.turnStart());

        var migrated = catalog.migrateToCurrent(session);

        assertEquals(4, migrated.header().formatVersion());
        assertTrue(SessionFormatVersion.of(migrated.header().formatVersion()).isCurrent());
        assertEquals(session.size(), migrated.size(), "事件数不变");
    }

    @Test
    @DisplayName("加载 V0 流依次执行 v0→v1→v2→v3→v4 迁移")
    void migrateV0ToCurrent() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSession(0);

        var migrated = catalog.migrateToCurrent(session);

        assertEquals(4, migrated.header().formatVersion());
    }

    @Test
    @DisplayName("加载 V4 流不迁移")
    void migrateV4NoOp() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSession(4);
        session.append(SessionEventFactory.turnStart());

        var migrated = catalog.migrateToCurrent(session);

        assertSame(session, migrated, "已是当前版本应返回原对象");
        assertEquals(4, migrated.header().formatVersion());
    }

    @Test
    @DisplayName("version > 4 抛 UnsupportedFormatException")
    void unsupportedVersionThrows() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSession(5);

        var ex = assertThrows(UnsupportedFormatException.class, () -> catalog.migrateToCurrent(session));
        assertEquals(5, ex.detectedVersion());
    }

    @Test
    @DisplayName("迁移链幂等：迁移两次等于一次")
    void idempotentChain() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSession(0);
        session.append(SessionEventFactory.turnStart());
        session.append(SessionEventFactory.turnEnd(new net.linlan.dsh.domain.session.event.model.valobj.TurnEndReason.Completed()));

        var migrated1 = catalog.migrateToCurrent(session);
        var migrated2 = catalog.migrateToCurrent(migrated1);

        assertEquals(migrated1.header().formatVersion(), migrated2.header().formatVersion());
        assertEquals(migrated1.size(), migrated2.size());
        assertSame(migrated1, migrated2, "第二次迁移应返回同一对象");
    }

    @Test
    @DisplayName("register 返回新 catalog（不可变）")
    void registerReturnsNewInstance() {
        var catalog = MigrationCatalog.defaultChain();
        var originalSize = catalog.chainSize();

        var extended = catalog.register(new V3ToV4Migration());

        assertEquals(originalSize, catalog.chainSize(), "原 catalog 不变");
        assertEquals(originalSize + 1, extended.chainSize(), "新 catalog 增加一步");
    }

    @Test
    @DisplayName("迁移保留 sessionId")
    void migrationPreservesSessionId() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSession(1);

        var migrated = catalog.migrateToCurrent(session);

        assertEquals(session.sessionId(), migrated.sessionId());
    }

    // ── helpers ──────────────────────────────────────────

    private static SessionLog buildSession(int formatVersion) {
        var header = new SessionHeader(
                3, "catalog-test", Instant.EPOCH, "/tmp", null, 0, null, 0, null, formatVersion);
        return new SessionLog("catalog-test", header);
    }

    private static SessionLog buildSessionWithEvents(int formatVersion) {
        var session = buildSession(formatVersion);
        session.append(SessionEventFactory.turnStart());
        session.append(SessionEventFactory.stepStart(1, 1));
        session.append(SessionEventFactory.stepEnd(1, 1));
        session.append(SessionEventFactory.turnEnd(new net.linlan.dsh.domain.session.event.model.valobj.TurnEndReason.Completed()));
        return session;
    }

    // ── T1.018: 每步迁移幂等性 ───────────────────────────

    @Test
    @DisplayName("V0ToV1 幂等：迁移两次等于一次")
    void v0ToV1IdempotentStep() {
        var m = new V0ToV1Migration();
        var session = buildSessionWithEvents(0);

        var once = m.migrate(session);
        var twice = m.migrate(once);

        assertEquals(once.header().formatVersion(), twice.header().formatVersion());
        assertEquals(once.size(), twice.size());
        assertSame(once, twice, "已是 V1 应返回同一对象");
    }

    @Test
    @DisplayName("V1ToV2 幂等：迁移两次等于一次")
    void v1ToV2IdempotentStep() {
        var m = new V1ToV2Migration();
        var session = buildSessionWithEvents(1);

        var once = m.migrate(session);
        var twice = m.migrate(once);

        assertSame(once, twice);
    }

    @Test
    @DisplayName("V2ToV3 幂等：迁移两次等于一次")
    void v2ToV3IdempotentStep() {
        var m = new V2ToV3Migration();
        var session = buildSessionWithEvents(2);

        var once = m.migrate(session);
        var twice = m.migrate(once);

        assertSame(once, twice);
    }

    @Test
    @DisplayName("V3ToV4 幂等：迁移两次等于一次")
    void v3ToV4IdempotentStep() {
        var m = new V3ToV4Migration();
        var session = buildSessionWithEvents(3);

        var once = m.migrate(session);
        var twice = m.migrate(once);

        assertSame(once, twice);
    }

    // ── T1.018: 全链回归测试 ─────────────────────────────

    @Test
    @DisplayName("V0 样本经全链后为 V4，事件完整保留")
    void v0FullChainRegression() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSessionWithEvents(0);
        var originalEventCount = session.size();
        var originalSessionId = session.sessionId();

        var migrated = catalog.migrateToCurrent(session);

        assertEquals(4, migrated.header().formatVersion(), "最终版本为 V4");
        assertEquals(originalEventCount, migrated.size(), "事件数不变");
        assertEquals(originalSessionId, migrated.sessionId(), "sessionId 不变");
        assertTrue(SessionFormatVersion.of(migrated.header().formatVersion()).isCurrent());
    }

    @Test
    @DisplayName("V1 样本经全链后为 V4")
    void v1FullChainRegression() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSessionWithEvents(1);

        var migrated = catalog.migrateToCurrent(session);

        assertEquals(4, migrated.header().formatVersion());
    }

    @Test
    @DisplayName("V3 样本经全链后为 V4")
    void v3FullChainRegression() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSessionWithEvents(3);

        var migrated = catalog.migrateToCurrent(session);

        assertEquals(4, migrated.header().formatVersion());
    }

    // ── T1.018: 冻结性测试 ───────────────────────────────

    @Test
    @DisplayName("全链迁移后输入不被修改：formatVersion 不变")
    void freezingPropertyFormatVersion() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSessionWithEvents(2);
        var originalFormatVersion = session.header().formatVersion();

        catalog.migrateToCurrent(session);

        assertEquals(originalFormatVersion, session.header().formatVersion(), "输入 formatVersion 不变");
    }

    @Test
    @DisplayName("全链迁移后输入不被修改：事件数不变")
    void freezingPropertyEventCount() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSessionWithEvents(0);
        var originalSize = session.size();

        catalog.migrateToCurrent(session);

        assertEquals(originalSize, session.size(), "输入事件数不变");
    }

    @Test
    @DisplayName("全链迁移后输入不被修改：sessionId 不变")
    void freezingPropertySessionId() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSessionWithEvents(0);
        var originalSessionId = session.sessionId();

        catalog.migrateToCurrent(session);

        assertEquals(originalSessionId, session.sessionId(), "输入 sessionId 不变");
    }

    @Test
    @DisplayName("全链迁移后输入不被修改：事件内容不变")
    void freezingPropertyEventContent() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSessionWithEvents(0);
        var originalEvents = session.events();

        catalog.migrateToCurrent(session);

        var afterEvents = session.events();
        assertEquals(originalEvents.size(), afterEvents.size());
        for (int i = 0; i < originalEvents.size(); i++) {
            assertEquals(originalEvents.get(i).type(), afterEvents.get(i).type(), "事件类型一致 at " + i);
            assertEquals(originalEvents.get(i).seq(), afterEvents.get(i).seq(), "事件 seq 一致 at " + i);
        }
    }

    // ── T1.018: 迁移链幂等性（全链） ─────────────────────

    @Test
    @DisplayName("全链幂等：V0 迁移两次等于一次")
    void fullChainIdempotentV0() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSessionWithEvents(0);

        var once = catalog.migrateToCurrent(session);
        var twice = catalog.migrateToCurrent(once);

        assertSame(once, twice, "第二次迁移应返回同一对象");
    }

    @Test
    @DisplayName("全链幂等：V2 迁移两次等于一次")
    void fullChainIdempotentV2() {
        var catalog = MigrationCatalog.defaultChain();
        var session = buildSessionWithEvents(2);

        var once = catalog.migrateToCurrent(session);
        var twice = catalog.migrateToCurrent(once);

        assertSame(once, twice);
    }
}
