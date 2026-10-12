package net.linlan.dsh.infrastructure.adapter.typert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("T2.029 SandboxedNodePtcProcess 沙箱 Node PTC 进程")
class SandboxedNodePtcProcessTest {

    private static boolean nodeAvailable() {
        try {
            Process p = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("默认构造函数使用 node 可执行文件")
    void defaultConstructorUsesNode() {
        Assumptions.assumeTrue(nodeAvailable());
        SandboxedNodePtcProcess process = new SandboxedNodePtcProcess();
        String output = process.execute("console.log('default-node')", Map.of());
        assertEquals("default-node", output);
    }

    @Test
    @DisplayName("构造函数 null/blank nodeExecutable 回退为 node")
    void nullNodeExecutableFallsBackToNode() {
        Assumptions.assumeTrue(nodeAvailable());
        SandboxedNodePtcProcess process = new SandboxedNodePtcProcess(null, null);
        String output = process.execute("console.log('fallback')", Map.of());
        assertEquals("fallback", output);
    }

    @Test
    @DisplayName("execute 传递环境变量到子进程")
    void executePassesEnvironmentVariables() {
        Assumptions.assumeTrue(nodeAvailable());
        SandboxedNodePtcProcess process = new SandboxedNodePtcProcess();
        String output = process.execute("console.log(process.env.MY_PTC_VAR)", Map.of("MY_PTC_VAR", "env-value"));
        assertEquals("env-value", output);
    }

    @Test
    @DisplayName("execute null script 抛 IllegalArgumentException")
    void executeNullScriptThrows() {
        SandboxedNodePtcProcess process = new SandboxedNodePtcProcess();
        assertThrows(IllegalArgumentException.class, () -> process.execute(null, Map.of()));
    }

    @Test
    @DisplayName("execute 空 script 抛 IllegalArgumentException")
    void executeBlankScriptThrows() {
        SandboxedNodePtcProcess process = new SandboxedNodePtcProcess();
        assertThrows(IllegalArgumentException.class, () -> process.execute("", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> process.execute("   ", Map.of()));
    }

    @Test
    @DisplayName("execute 不存在的 node 可执行文件抛 RuntimeException")
    void executeWithNonexistentNodeThrowsRuntimeException() {
        SandboxedNodePtcProcess process = new SandboxedNodePtcProcess("nonexistent-node-xyz-123", "");
        assertThrows(RuntimeException.class, () -> process.execute("console.log(1)", Map.of()));
    }

    @Test
    @DisplayName("execute null env 不抛异常（node 可用时）")
    void executeNullEnvDoesNotThrow() {
        Assumptions.assumeTrue(nodeAvailable());
        SandboxedNodePtcProcess process = new SandboxedNodePtcProcess();
        String output = process.execute("console.log('no-env')", null);
        assertEquals("no-env", output);
    }

    @Test
    @DisplayName("execute env 含 null 值被跳过")
    void executeEnvWithNullValueSkipped() {
        Assumptions.assumeTrue(nodeAvailable());
        SandboxedNodePtcProcess process = new SandboxedNodePtcProcess();
        Map<String, Object> env = new java.util.HashMap<>();
        env.put("KEEP", "kept");
        env.put("DROP", null);
        String output = process.execute("console.log(process.env.KEEP)", env);
        assertEquals("kept", output);
    }

    @Test
    @DisplayName("SandboxedNodePtcProcess 实现 IPtcRuntimePort")
    void implementsIPtcRuntimePort() {
        SandboxedNodePtcProcess process = new SandboxedNodePtcProcess();
        assertTrue(process instanceof net.linlan.dsh.domain.typert.adapter.port.IPtcRuntimePort);
    }
}
