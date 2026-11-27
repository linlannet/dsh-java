package net.linlan.dsh.domain.agent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("系统消息来源章节累计")
class SystemMessageAccumulatorTest {

    @Test
    @DisplayName("后写覆盖同来源同名章节但不覆盖其他来源")
    void mergesSectionsBySource() {
        var accumulator = new SystemMessageAccumulator();
        accumulator.update("runtime-context", List.of(
                new SystemMessageSection("shared", "runtime-v1"),
                new SystemMessageSection("cwd", "cwd=/repo")));
        accumulator.update("plugin-a",
                new SystemMessageSection("shared", "plugin-a"));
        accumulator.update("runtime-context",
                new SystemMessageSection("shared", "runtime-v2"));

        assertEquals(List.of(
                        new SystemMessageSection("shared", "runtime-v2"),
                        new SystemMessageSection("cwd", "cwd=/repo"),
                        new SystemMessageSection("shared", "plugin-a")),
                accumulator.sections());
        assertEquals(2, accumulator.sectionsBySource().get("runtime-context").size());
    }

    @Test
    @DisplayName("运行时快照与累计器使用同一份章节")
    void snapshotUsesAccumulatedSections() {
        var accumulator = new SystemMessageAccumulator();
        var snapshot = new RuntimeContextSnapshot();
        accumulator.update("runtime-context",
                new SystemMessageSection("cwd", "cwd=/repo"));
        accumulator.update("plugin-a",
                new SystemMessageSection("notice", "plugin-ready"));

        String rendered = snapshot.renderAccumulator("agent-1", accumulator).orElseThrow();

        assertEquals(accumulator.sections(), snapshot.sections("agent-1"));
        assertTrue(rendered.contains("cwd=/repo"));
        assertTrue(rendered.contains("plugin-ready"));

        accumulator.update("plugin-a",
                new SystemMessageSection("notice", "plugin-updated"));
        String updated = snapshot.renderAccumulator("agent-1", accumulator).orElseThrow();
        assertFalse(updated.contains("plugin-ready"));
        assertTrue(updated.contains("plugin-updated"));
        assertTrue(updated.contains("cwd=/repo"));
        assertEquals(accumulator.sections(), snapshot.sections("agent-1"));
    }

    @Test
    @DisplayName("装配器合并注册上下文与外部来源通知")
    void assemblerIncludesNotificationsFromEverySource() {
        var assembler = new ScopedSystemPromptAssembler();
        assembler.registerContext(new ScopedSystemPromptAssembler.PromptContext(
                "runtime-context", 10, "cwd=/repo"));
        assembler.systemMessages().update("plugin-a",
                new SystemMessageSection("notice", "plugin-ready"));

        String system = assembler.assemble("agent-1", null, "/repo", List.of()).join().system();

        assertTrue(system.contains("cwd=/repo"));
        assertTrue(system.contains("plugin-ready"));
        assertEquals(assembler.systemMessages().sections(),
                assembler.runtimeSnapshots().sections("agent-1"));
    }
}
