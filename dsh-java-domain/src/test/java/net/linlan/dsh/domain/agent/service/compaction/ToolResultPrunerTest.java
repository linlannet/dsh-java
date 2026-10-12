package net.linlan.dsh.domain.agent.service.compaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolResultPruner head/middle/tail 剪枝")
class ToolResultPrunerTest {

    private final ToolResultPruner pruner = new ToolResultPruner();

    @Test
    @DisplayName("短文本不剪枝")
    void shortTextNotPruned() {
        String result = pruner.prune("short text", 100);
        assertEquals("short text", result);
    }

    @Test
    @DisplayName("超阈值文本被剪枝")
    void longTextPruned() {
        String input = "x".repeat(5000);
        String result = pruner.prune(input, 1000);
        assertTrue(result.length() < input.length());
        assertTrue(result.contains("[pruned"));
    }

    @Test
    @DisplayName("剪枝后总长度低于阈值")
    void prunedLengthBelowThreshold() {
        String input = "x".repeat(10000);
        String result = pruner.prune(input, 1000);
        assertTrue(result.length() < 1000 + 100);
    }

    @Test
    @DisplayName("剪枝保留首尾片段")
    void prunedKeepsHeadAndTail() {
        String head = "HEADHEADHEAD";
        String tail = "TAILTAILTAIL";
        String input = head + "MIDDLE".repeat(1000) + tail;
        String result = pruner.prune(input, 200);
        assertTrue(result.contains("HEAD"));
        assertTrue(result.contains("TAIL"));
    }

    @Test
    @DisplayName("null 输入返回 null")
    void nullInputReturnsNull() {
        assertNull(pruner.prune(null, 100));
    }

    @Test
    @DisplayName("headMiddleTail 保留首尾")
    void headMiddleTailKeepsHeadAndTail() {
        String input = "A".repeat(100) + "B".repeat(1000) + "C".repeat(100);
        String result = pruner.headMiddleTail(input, 300);
        assertTrue(result.startsWith("A"));
        assertTrue(result.endsWith("C"));
    }

    @Test
    @DisplayName("默认阈值剪枝")
    void defaultThresholdPrune() {
        String input = "x".repeat(5000);
        String result = pruner.prune(input);
        assertTrue(result.length() < input.length());
    }

    @Test
    @DisplayName("空字符串不剪枝")
    void emptyStringNotPruned() {
        assertEquals("", pruner.prune("", 100));
    }
}
