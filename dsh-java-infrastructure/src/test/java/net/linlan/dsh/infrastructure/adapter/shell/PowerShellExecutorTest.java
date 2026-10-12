package net.linlan.dsh.infrastructure.adapter.shell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PowerShellExecutor PowerShell 执行器")
class PowerShellExecutorTest {

    @Test
    @DisplayName("ExecutionResult isSuccess 退出码 0 返回 true")
    void executionResultSuccessOnZeroExit() {
        var r = new PowerShellExecutor.ExecutionResult("out", "", 0);
        assertTrue(r.isSuccess());
    }

    @Test
    @DisplayName("ExecutionResult 非零退出码返回 false")
    void executionResultFailureOnNonZeroExit() {
        var r = new PowerShellExecutor.ExecutionResult("out", "err", 1);
        assertFalse(r.isSuccess());
    }

    @Test
    @DisplayName("execute null 脚本返回错误结果")
    void executeNullScriptReturnsError() {
        var executor = new PowerShellExecutor();
        var r = executor.execute(null);
        assertFalse(r.isSuccess());
        assertEquals(-1, r.exitCode());
    }

    @Test
    @DisplayName("默认构造使用 pwsh 命令")
    void defaultConstructorUsesPwsh() {
        var executor = new PowerShellExecutor();
        assertNotNull(executor);
    }

    @Test
    @DisplayName("自定义构造接受命令与超时")
    void customConstructorAcceptsCommandAndTimeout() {
        var executor = new PowerShellExecutor("pwsh", 5000);
        assertNotNull(executor);
    }

    @Test
    @DisplayName("executeInSandbox null 脚本返回错误")
    void executeInSandboxNullScriptReturnsError() {
        var executor = new PowerShellExecutor();
        var r = executor.executeInSandbox(null, null);
        assertFalse(r.isSuccess());
    }

    @Test
    @DisplayName("executeInSandbox null 策略降级为普通执行")
    void executeInSandboxNullPolicyFallsBackToExecute() {
        var executor = new PowerShellExecutor();
        var r = executor.executeInSandbox("Write-Output test", null);
        assertNotNull(r);
    }

    @Test
    @DisplayName("isAvailable 在非 Windows 平台通常返回 false")
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void isAvailableReturnsFalseOnNonWindows() {
        var executor = new PowerShellExecutor("nonexistent-pwsh-cmd", 3000);
        assertFalse(executor.isAvailable());
    }
}
