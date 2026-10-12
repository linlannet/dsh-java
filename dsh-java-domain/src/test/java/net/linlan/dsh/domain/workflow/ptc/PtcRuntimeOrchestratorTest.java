package net.linlan.dsh.domain.workflow.ptc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PtcRuntimeOrchestrator 沙箱 Node PTC 运行时编排")
class PtcRuntimeOrchestratorTest {

    @Test
    @DisplayName("构造参数校验")
    void constructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new PtcRuntimeOrchestrator(null, "/script.js"));
        assertThrows(IllegalArgumentException.class, () -> new PtcRuntimeOrchestrator("", "/script.js"));
        assertThrows(IllegalArgumentException.class, () -> new PtcRuntimeOrchestrator("node", null));
        assertThrows(IllegalArgumentException.class, () -> new PtcRuntimeOrchestrator("node", "  "));
    }

    @Test
    @DisplayName("start 后状态为 RUNNING")
    void startTransitionsToRunning() {
        var orch = new PtcRuntimeOrchestrator("node", "/sandbox.js");
        assertEquals(PtcRuntimeOrchestrator.State.CREATED, orch.state());
        orch.start();
        assertEquals(PtcRuntimeOrchestrator.State.RUNNING, orch.state());
    }

    @Test
    @DisplayName("重复 start 抛异常")
    void doubleStartThrows() {
        var orch = new PtcRuntimeOrchestrator("node", "/sandbox.js");
        orch.start();
        assertThrows(IllegalStateException.class, orch::start);
    }

    @Test
    @DisplayName("orchestrate 未启动时抛异常")
    void orchestrateBeforeStartThrows() {
        var orch = new PtcRuntimeOrchestrator("node", "/sandbox.js");
        assertThrows(IllegalStateException.class, () -> orch.orchestrate("script"));
    }

    @Test
    @DisplayName("orchestrate 空脚本抛异常")
    void orchestrateBlankScriptThrows() {
        var orch = new PtcRuntimeOrchestrator("node", "/sandbox.js");
        orch.start();
        assertThrows(IllegalArgumentException.class, () -> orch.orchestrate(""));
        assertThrows(IllegalArgumentException.class, () -> orch.orchestrate(null));
    }

    @Test
    @DisplayName("orchestrate 返回成功结果并计数")
    void orchestrateSucceeds() {
        var orch = new PtcRuntimeOrchestrator("node", "/sandbox.js");
        orch.start();
        var r1 = orch.orchestrate("workflow1");
        var r2 = orch.orchestrate("workflow2");
        assertTrue(r1.success());
        assertEquals("workflow1", r1.script());
        assertEquals(0, r1.exitCode());
        assertTrue(r2.success());
        assertEquals(2, orch.orchestrationCount());
    }

    @Test
    @DisplayName("orchestrate 结果 metadata 包含索引")
    void orchestrateMetadataContainsIndex() {
        var orch = new PtcRuntimeOrchestrator("node", "/sandbox.js");
        orch.start();
        var r = orch.orchestrate("workflow");
        assertEquals(1, r.metadata().get("orchestrationIndex"));
    }

    @Test
    @DisplayName("stop 后状态为 STOPPED")
    void stopTransitionsToStopped() {
        var orch = new PtcRuntimeOrchestrator("node", "/sandbox.js");
        orch.start();
        orch.stop();
        assertEquals(PtcRuntimeOrchestrator.State.STOPPED, orch.state());
    }

    @Test
    @DisplayName("stop 幂等")
    void stopIdempotent() {
        var orch = new PtcRuntimeOrchestrator("node", "/sandbox.js");
        orch.start();
        orch.stop();
        orch.stop();
        assertEquals(PtcRuntimeOrchestrator.State.STOPPED, orch.state());
    }

    @Test
    @DisplayName("stop 后 orchestrate 抛异常")
    void orchestrateAfterStopThrows() {
        var orch = new PtcRuntimeOrchestrator("node", "/sandbox.js");
        orch.start();
        orch.stop();
        assertThrows(IllegalStateException.class, () -> orch.orchestrate("script"));
    }

    @Test
    @DisplayName("nodeExecutable 和 sandboxScriptPath 可访问")
    void configAccessors() {
        var orch = new PtcRuntimeOrchestrator("node", "/sandbox.js");
        assertEquals("node", orch.nodeExecutable());
        assertEquals("/sandbox.js", orch.sandboxScriptPath());
    }
}
