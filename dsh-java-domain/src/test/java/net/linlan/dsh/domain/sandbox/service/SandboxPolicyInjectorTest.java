package net.linlan.dsh.domain.sandbox.service;

import net.linlan.dsh.domain.sandbox.ISandboxPolicyService;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxExecutionPolicy;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxMode;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxPolicyRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SandboxPolicyInjector 沙箱策略注入")
class SandboxPolicyInjectorTest {

    private static final class StubPolicyService implements ISandboxPolicyService {
        final SandboxExecutionPolicy policy;

        StubPolicyService(SandboxExecutionPolicy policy) {
            this.policy = policy;
        }

        @Override
        public SandboxExecutionPolicy resolve(SandboxPolicyRequest request) {
            return policy;
        }
    }

    @Test
    @DisplayName("injectPolicy 返回包含工具名和模式的摘要")
    void injectPolicyContainsToolAndMode() {
        var injector = new SandboxPolicyInjector(new StubPolicyService(SandboxExecutionPolicy.readOnly()));
        String result = injector.injectPolicy("fs_read", "{\"path\":\"/tmp\"}");
        assertNotNull(result);
        assertTrue(result.contains("tool=fs_read"));
        assertTrue(result.contains("mode=READ_ONLY"));
        assertTrue(result.contains("arguments={\"path\":\"/tmp\"}"));
    }

    @Test
    @DisplayName("workspaceWrite 策略摘要包含 workspaceRoot")
    void workspaceWriteSummaryContainsRoot() {
        Path root = Path.of("/workspace");
        var injector = new SandboxPolicyInjector(new StubPolicyService(SandboxExecutionPolicy.workspaceWrite(root)));
        String result = injector.injectPolicy("fs_write", "{}");
        assertTrue(result.contains("mode=WORKSPACE_WRITE"));
        assertTrue(result.contains("workspaceRoot=/workspace"));
    }

    @Test
    @DisplayName("fullAccess 策略摘要包含 DANGER_FULL_ACCESS")
    void fullAccessSummaryContainsMode() {
        var injector = new SandboxPolicyInjector(new StubPolicyService(SandboxExecutionPolicy.fullAccess()));
        String result = injector.injectPolicy("shell", "");
        assertTrue(result.contains("mode=DANGER_FULL_ACCESS"));
    }

    @Test
    @DisplayName("null toolName 和 arguments 安全处理")
    void nullToolNameAndArgumentsHandledSafely() {
        var injector = new SandboxPolicyInjector(new StubPolicyService(SandboxExecutionPolicy.readOnly()));
        String result = injector.injectPolicy(null, null);
        assertNotNull(result);
        assertTrue(result.contains("tool="));
        assertTrue(result.contains("arguments="));
    }

    @Test
    @DisplayName("null policyService 抛异常")
    void nullPolicyServiceRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SandboxPolicyInjector(null));
    }

    @Test
    @DisplayName("带 request 的重载使用 request 解析策略")
    void injectPolicyWithRequestUsesRequest() {
        var injector = new SandboxPolicyInjector(new StubPolicyService(SandboxExecutionPolicy.fullAccess()));
        SandboxPolicyRequest request = new SandboxPolicyRequest(Optional.of(Path.of("/custom")), Optional.of(SandboxMode.DANGER_FULL_ACCESS));
        String result = injector.injectPolicy("shell", "{}", request);
        assertTrue(result.contains("mode=DANGER_FULL_ACCESS"));
    }

    @Test
    @DisplayName("带 null request 的重载回退到默认解析")
    void injectPolicyWithNullRequestFallsBack() {
        var injector = new SandboxPolicyInjector(new StubPolicyService(SandboxExecutionPolicy.readOnly()));
        String result = injector.injectPolicy("fs_read", "{}", null);
        assertTrue(result.contains("mode=READ_ONLY"));
    }
}
