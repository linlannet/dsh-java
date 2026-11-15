package net.linlan.dsh.domain.agent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("运行时上下文提醒")
class RuntimeContextSnapshotTest {

    @Test
    @DisplayName("首份与后续快照文案不同且包裹 system-reminder")
    void firstAndSubsequentSnapshots() {
        var snapshot = new RuntimeContextSnapshot();

        String first = snapshot.render("agent-1", List.of("cwd=/repo")).orElseThrow();
        String second = snapshot.render("agent-1", List.of("cwd=/repo")).orElseThrow();

        assertTrue(first.startsWith("<system-reminder>"));
        assertTrue(first.contains(RuntimeContextSnapshot.FIRST_PREFIX));
        assertTrue(first.contains("cwd=/repo"));
        assertTrue(first.endsWith("</system-reminder>"));

        assertTrue(second.contains(RuntimeContextSnapshot.SUBSEQUENT_PREFIX));
        assertFalse(second.contains(RuntimeContextSnapshot.FIRST_PREFIX));
    }

    @Test
    @DisplayName("清空提醒输出 form=snapshot 与 sections=[]")
    void clearSnapshot() {
        var snapshot = new RuntimeContextSnapshot();
        snapshot.render("agent-1", List.of("cwd=/repo"));

        String cleared = snapshot.render("agent-1", List.of()).orElseThrow();
        assertTrue(cleared.contains("form=snapshot"));
        assertTrue(cleared.contains("sections=[]"));
        assertTrue(cleared.startsWith("<system-reminder>"));

        assertTrue(snapshot.render("agent-1", List.of()).isEmpty());
    }

    @Test
    @DisplayName("无历史快照时清空不输出提醒")
    void clearWithoutHistoryIsEmpty() {
        assertTrue(new RuntimeContextSnapshot().render("agent-1", List.of()).isEmpty());
    }

    @Test
    @DisplayName("旧快照缺少 sections 时仅刷新一次且不改写前缀")
    void legacySnapshotRefreshesOnce() {
        var snapshot = new RuntimeContextSnapshot();
        snapshot.seedLegacy("agent-1", "legacy-prefix");

        String first = snapshot.render("agent-1", List.of("cwd=/repo")).orElseThrow();
        String second = snapshot.render("agent-1", List.of("cwd=/repo")).orElseThrow();

        assertTrue(first.contains("legacy-prefix"));
        assertTrue(second.contains("legacy-prefix"));
        assertEquals(1, snapshot.refreshCount("agent-1"));
    }

    @Test
    @DisplayName("assembler 首份与后续快照接入")
    void assemblerUsesSnapshot() {
        var assembler = new ScopedSystemPromptAssembler();
        assembler.registerContext(new ScopedSystemPromptAssembler.PromptContext(
                "runtime:cwd", 10, "cwd=/repo"));

        String first = assembler.assemble("agent-1", null, "/repo", List.of()).join().system();
        String second = assembler.assemble("agent-1", null, "/repo", List.of()).join().system();

        assertTrue(first.contains(RuntimeContextSnapshot.FIRST_PREFIX));
        assertTrue(second.contains(RuntimeContextSnapshot.SUBSEQUENT_PREFIX));
    }

    @Test
    @DisplayName("assembler 抑制运行时上下文后输出清空提醒")
    void assemblerClearsWhenSuppressed() {
        var assembler = new ScopedSystemPromptAssembler();
        assembler.registerContext(new ScopedSystemPromptAssembler.PromptContext(
                "runtime:cwd", 10, "cwd=/repo"));
        assembler.assemble("agent-1", null, "/repo", List.of()).join();

        assembler.suppressRuntimeContext();
        String cleared = assembler.assemble("agent-1", null, "/repo", List.of()).join().system();

        assertTrue(cleared.contains("form=snapshot"));
        assertTrue(cleared.contains("sections=[]"));
    }
}
