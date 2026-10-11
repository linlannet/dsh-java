package net.linlan.dsh.domain.runtime.meter.model.valobj;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T1.027 Token 快照模型与仓储端口")
class TokenSnapshotTest {

    @Test
    @DisplayName("TokenSnapshot.initial() 创建零基快照")
    void initialSnapshot() {
        var snap = TokenSnapshot.initial();
        assertEquals(-1, snap.lastEventStepIndex());
        assertEquals(0, snap.totalTokens().inputTokens());
        assertEquals(0, snap.totalTokens().outputTokens());
        assertEquals(0, snap.modelCalls());
    }

    @Test
    @DisplayName("TokenSnapshot.advanceTo() 前进并累加")
    void advanceTo() {
        var snap = TokenSnapshot.initial();
        var delta = new TokenUsage(100, 50);
        var advanced = snap.advanceTo(5, delta, 2);

        assertEquals(5, advanced.lastEventStepIndex());
        assertEquals(100, advanced.totalTokens().inputTokens());
        assertEquals(50, advanced.totalTokens().outputTokens());
        assertEquals(2, advanced.modelCalls());
    }

    @Test
    @DisplayName("TokenSnapshot.advanceTo() 多次累加")
    void advanceToMultiple() {
        var snap = TokenSnapshot.initial();
        snap = snap.advanceTo(3, new TokenUsage(10, 5), 1);
        snap = snap.advanceTo(7, new TokenUsage(20, 10), 2);

        assertEquals(7, snap.lastEventStepIndex());
        assertEquals(30, snap.totalTokens().inputTokens());
        assertEquals(15, snap.totalTokens().outputTokens());
        assertEquals(3, snap.modelCalls());
    }

    @Test
    @DisplayName("TokenSnapshot null totalTokens 归一化为零")
    void nullTotalTokensNormalized() {
        var snap = new TokenSnapshot(0, null, 0);
        assertNotNull(snap.totalTokens());
        assertEquals(0, snap.totalTokens().inputTokens());
    }

    @Test
    @DisplayName("ReplayContext.live() 创建非重放上下文")
    void liveContext() {
        var ctx = ReplayContext.live();
        assertFalse(ctx.isReplay());
        assertEquals(Long.MAX_VALUE, ctx.upToStepIndex());
    }

    @Test
    @DisplayName("ReplayContext.replay() 创建重放上下文")
    void replayContext() {
        var ctx = ReplayContext.replay(42);
        assertTrue(ctx.isReplay());
        assertEquals(42, ctx.upToStepIndex());
    }

    @Test
    @DisplayName("ArchUnit：meter 端口不依赖 infrastructure")
    void meterPortMustNotDependOnInfrastructure() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh.domain.runtime.meter");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("net.linlan.dsh.domain.runtime.meter..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "net.linlan.dsh.infrastructure..")
                .check(classes);
    }
}
