package net.linlan.dsh.domain.tool.shell.persistent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PersistentShellTool 持久化 Shell")
class PersistentShellToolTest {

    @Test
    @DisplayName("execute 简单 echo 命令返回输出")
    void executeEchoReturnsOutput() {
        try (var shell = new PersistentShellTool()) {
            var r = shell.execute("echo hello");
            assertTrue(r.isSuccess());
            assertEquals("hello", r.stdout().trim());
        }
    }

    @Test
    @DisplayName("cd 后 pwd 反映目录变化")
    void cdThenPwdReflectsChange() {
        try (var shell = new PersistentShellTool()) {
            shell.cd("/tmp");
            String pwd = shell.pwd();
            assertNotNull(pwd);
            assertTrue(pwd.contains("tmp") || pwd.equals("/tmp") || pwd.startsWith("/private/tmp"));
        }
    }

    @Test
    @DisplayName("setEnv 后环境变量可用")
    void setEnvMakesVariableAvailable() {
        try (var shell = new PersistentShellTool()) {
            shell.setEnv("DSH_TEST_VAR", "myvalue");
            var r = shell.execute("echo $DSH_TEST_VAR");
            assertTrue(r.isSuccess());
            assertEquals("myvalue", r.stdout().trim());
        }
    }

    @Test
    @DisplayName("setEnv blank key 抛出异常")
    void setEnvBlankKeyThrows() {
        try (var shell = new PersistentShellTool()) {
            assertThrows(IllegalArgumentException.class, () -> shell.setEnv("", "v"));
        }
    }

    @Test
    @DisplayName("cd blank path 抛出异常")
    void cdBlankPathThrows() {
        try (var shell = new PersistentShellTool()) {
            assertThrows(IllegalArgumentException.class, () -> shell.cd(""));
        }
    }

    @Test
    @DisplayName("execute null 或 blank 返回成功空结果")
    void executeBlankReturnsSuccess() {
        try (var shell = new PersistentShellTool()) {
            var r = shell.execute("");
            assertTrue(r.isSuccess());
        }
    }

    @Test
    @DisplayName("close 后 execute 返回错误")
    void executeAfterCloseReturnsError() {
        var shell = new PersistentShellTool();
        shell.close();
        var r = shell.execute("echo test");
        assertFalse(r.isSuccess());
    }

    @Test
    @DisplayName("envOverrides 返回已设置的环境变量")
    void envOverridesReturnsSetVariables() {
        try (var shell = new PersistentShellTool()) {
            shell.setEnv("DSH_A", "1");
            shell.setEnv("DSH_B", "2");
            var env = shell.envOverrides();
            assertEquals("1", env.get("DSH_A"));
            assertEquals("2", env.get("DSH_B"));
        }
    }

    @Test
    @DisplayName("跨调用保持状态")
    void statePersistsAcrossCalls() {
        try (var shell = new PersistentShellTool()) {
            shell.execute("X=42");
            var r = shell.execute("echo $X");
            assertTrue(r.isSuccess());
            assertEquals("42", r.stdout().trim());
        }
    }
}
