package net.linlan.dsh.domain.session.event.service.migration;

import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.model.valobj.SessionFormatVersion;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("V0→V4 冻结迁移实现")
class SessionFormatMigrationTest {

    @Test
    @DisplayName("V0ToV1: fromVersion=0, toVersion=1")
    void v0ToV1Versions() {
        var m = new V0ToV1Migration();
        assertEquals(0, m.fromVersion());
        assertEquals(1, m.toVersion());
    }

    @Test
    @DisplayName("V1ToV2: fromVersion=1, toVersion=2")
    void v1ToV2Versions() {
        var m = new V1ToV2Migration();
        assertEquals(1, m.fromVersion());
        assertEquals(2, m.toVersion());
    }

    @Test
    @DisplayName("V2ToV3: fromVersion=2, toVersion=3")
    void v2ToV3Versions() {
        var m = new V2ToV3Migration();
        assertEquals(2, m.fromVersion());
        assertEquals(3, m.toVersion());
    }

    @Test
    @DisplayName("V3ToV4: fromVersion=3, toVersion=4")
    void v3ToV4Versions() {
        var m = new V3ToV4Migration();
        assertEquals(3, m.fromVersion());
        assertEquals(4, m.toVersion());
    }

    @Test
    @DisplayName("V0ToV1: 纯函数 — 输入不被修改")
    void v0ToV1PureFunction() {
        var session = buildSession(0);
        var originalEventCount = session.size();
        var originalFormatVersion = session.header().formatVersion();

        new V0ToV1Migration().migrate(session);

        assertEquals(originalEventCount, session.size(), "输入事件数不变");
        assertEquals(originalFormatVersion, session.header().formatVersion(), "输入 formatVersion 不变");
    }

    @Test
    @DisplayName("V0ToV1: 幂等 — 对已是 V1 的输入无副作用")
    void v0ToV1Idempotent() {
        var session = buildSession(1);
        var migrated = new V0ToV1Migration().migrate(session);

        assertEquals(1, migrated.header().formatVersion(), "已是 V1 不再迁移");
        assertEquals(session.size(), migrated.size(), "事件数不变");
    }

    @Test
    @DisplayName("V0ToV1: 迁移后 formatVersion=1")
    void v0ToV1MigratesFormatVersion() {
        var session = buildSession(0);
        var migrated = new V0ToV1Migration().migrate(session);

        assertEquals(1, migrated.header().formatVersion());
    }

    @Test
    @DisplayName("V1ToV2: 纯函数 — 输入不被修改")
    void v1ToV2PureFunction() {
        var session = buildSession(1);
        var originalFormatVersion = session.header().formatVersion();

        new V1ToV2Migration().migrate(session);

        assertEquals(originalFormatVersion, session.header().formatVersion(), "输入 formatVersion 不变");
    }

    @Test
    @DisplayName("V1ToV2: 幂等")
    void v1ToV2Idempotent() {
        var session = buildSession(2);
        var migrated = new V1ToV2Migration().migrate(session);

        assertEquals(2, migrated.header().formatVersion());
    }

    @Test
    @DisplayName("V2ToV3: 纯函数 — 输入不被修改")
    void v2ToV3PureFunction() {
        var session = buildSession(2);
        var originalFormatVersion = session.header().formatVersion();

        new V2ToV3Migration().migrate(session);

        assertEquals(originalFormatVersion, session.header().formatVersion());
    }

    @Test
    @DisplayName("V2ToV3: 幂等")
    void v2ToV3Idempotent() {
        var session = buildSession(3);
        var migrated = new V2ToV3Migration().migrate(session);

        assertEquals(3, migrated.header().formatVersion());
    }

    @Test
    @DisplayName("V3ToV4: 纯函数 — 输入不被修改")
    void v3ToV4PureFunction() {
        var session = buildSession(3);
        var originalFormatVersion = session.header().formatVersion();

        new V3ToV4Migration().migrate(session);

        assertEquals(originalFormatVersion, session.header().formatVersion());
    }

    @Test
    @DisplayName("V3ToV4: 幂等")
    void v3ToV4Idempotent() {
        var session = buildSession(4);
        var migrated = new V3ToV4Migration().migrate(session);

        assertEquals(4, migrated.header().formatVersion());
    }

    @Test
    @DisplayName("V3ToV4: 迁移后 formatVersion=4")
    void v3ToV4MigratesFormatVersion() {
        var session = buildSession(3);
        var migrated = new V3ToV4Migration().migrate(session);

        assertEquals(4, migrated.header().formatVersion());
        assertTrue(SessionFormatVersion.of(migrated.header().formatVersion()).isCurrent());
    }

    @Test
    @DisplayName("全链 V0→V4 迁移：依次执行四步迁移后 formatVersion=4")
    void fullChainV0ToV4() {
        var session = buildSession(0);
        var m1 = new V0ToV1Migration().migrate(session);
        var m2 = new V1ToV2Migration().migrate(m1);
        var m3 = new V2ToV3Migration().migrate(m2);
        var m4 = new V3ToV4Migration().migrate(m3);

        assertEquals(4, m4.header().formatVersion());
        assertEquals(session.size(), m4.size(), "事件数在整个迁移链中不变");
        assertEquals(session.sessionId(), m4.sessionId(), "sessionId 不变");
    }

    @Test
    @DisplayName("迁移保留事件内容")
    void migrationPreservesEvents() {
        var session = buildSession(0);
        session.append(SessionEventFactory.turnStart());
        session.append(SessionEventFactory.stepStart(1, 1));
        session.append(SessionEventFactory.stepEnd(1, 1));
        session.append(SessionEventFactory.turnEnd(new net.linlan.dsh.domain.session.event.model.valobj.TurnEndReason.Completed()));

        var originalEvents = session.events();
        var migrated = new V0ToV1Migration().migrate(session);
        var migratedEvents = migrated.events();

        assertEquals(originalEvents.size(), migratedEvents.size(), "事件数相同");
        for (int i = 0; i < originalEvents.size(); i++) {
            assertEquals(originalEvents.get(i).type(), migratedEvents.get(i).type(),
                    "事件类型一致 at index " + i);
            assertEquals(originalEvents.get(i).seq(), migratedEvents.get(i).seq(),
                    "事件序列号一致 at index " + i);
        }
    }

    // ── helpers ──────────────────────────────────────────

    private static SessionLog buildSession(int formatVersion) {
        var header = new SessionHeader(
                3, "migration-test", Instant.EPOCH, "/tmp", null, 0, null, 0, null, formatVersion);
        return new SessionLog("migration-test", header);
    }
}
