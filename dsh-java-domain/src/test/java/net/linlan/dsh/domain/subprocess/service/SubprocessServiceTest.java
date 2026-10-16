package net.linlan.dsh.domain.subprocess.service;

import net.linlan.dsh.domain.subprocess.adapter.port.ISubprocessExecutorPort;
import net.linlan.dsh.domain.subprocess.model.valobj.SubprocessResult;
import net.linlan.dsh.domain.subprocess.model.valobj.SubprocessSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubprocessService 子进程管理")
class SubprocessServiceTest {

    private static class StubExecutor implements ISubprocessExecutorPort {
        SubprocessSpec lastSpec;

        @Override
        public SubprocessResult execute(SubprocessSpec spec) {
            lastSpec = spec;
            return SubprocessResult.of(0, "ok", "");
        }

        @Override
        public SubprocessResult collect(SubprocessSpec spec, long timeoutMs) {
            return execute(spec);
        }
    }

    @Test
    @DisplayName("resolveExecutable 查找可执行文件")
    void resolveExecutable() {
        var service = new SubprocessService(new StubExecutor());
        String result = service.resolveExecutable("java");
        assertNotNull(result);
    }

    @Test
    @DisplayName("spawn 清理环境中的凭据与 DSH_* 变量")
    void spawnCleansEnvironment() {
        var executor = new StubExecutor();
        var service = new SubprocessService(executor);

        service.spawn(SubprocessSpec.of(List.of("echo", "hello")));
        assertNotNull(executor.lastSpec);
        assertFalse(containsCredential(executor.lastSpec.envOverride()));
    }

    @Test
    @DisplayName("spawn 应用显式 envOverride 覆盖清理后的环境")
    void spawnAppliesOverride() {
        var executor = new StubExecutor();
        var service = new SubprocessService(executor);

        service.spawn(new SubprocessSpec(List.of("echo"), null, null,
                Map.of("CUSTOM_VAR", "value"), 0, null));

        assertEquals("value", executor.lastSpec.envOverride().get("CUSTOM_VAR"));
    }

    @Test
    @DisplayName("terminate 返回终止结果")
    void terminate() {
        var service = new SubprocessService(new StubExecutor());
        SubprocessResult result = service.terminate("handle-1", 5000);
        assertNotNull(result);
        assertTrue(result.success());
    }

    private boolean containsCredential(Map<String, String> env) {
        return env.keySet().stream().anyMatch(k ->
                k.toUpperCase().contains("TOKEN") || k.toUpperCase().contains("SECRET")
                        || k.toUpperCase().contains("PASSWORD") || k.startsWith("DSH_"));
    }
}
