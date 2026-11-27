package net.linlan.dsh.domain.common.error;

import net.linlan.dsh.domain.hooks.model.error.HookError;
import net.linlan.dsh.domain.tool.goal.RalphLoopError;
import net.linlan.dsh.domain.tool.goal.RalphLoopTool;
import net.linlan.dsh.domain.tool.mcp.McpReconnectExhaustedException;
import net.linlan.dsh.domain.tool.model.error.ToolArgsError;
import net.linlan.dsh.domain.tool.web.browser.BrowserUseError;
import net.linlan.dsh.domain.tool.web.browser.BrowserUseTool;
import net.linlan.dsh.domain.tool.web.browser.adapter.port.IBrowserUsePort;
import net.linlan.dsh.domain.tool.web.computer.ComputerUseError;
import net.linlan.dsh.domain.tool.web.computer.ComputerUseTool;
import net.linlan.dsh.domain.tool.web.computer.adapter.port.IComputerUsePort;
import net.linlan.dsh.domain.tool.worktree.EnterWorktreeTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("子系统错误类统一继承 HarnessError")
class SubsystemErrorInheritanceTest {

    @Test
    @DisplayName("Worktree 非 Git 仓库错误保留错误码")
    void worktreeNotGitRepo() throws Exception {
        Path directory = Files.createTempDirectory("dsh-worktree-test");
        var tool = new EnterWorktreeTool();

        var error = assertThrows(EnterWorktreeTool.NotGitRepoException.class,
                () -> tool.enter(directory.toString(), "feature"));

        assertInstanceOf(HarnessError.class, error);
        assertEquals(EnterWorktreeTool.NotGitRepoException.NOT_GIT_REPO, error.errorCode());
        assertEquals("NotGitRepoException", error.errorName());
    }

    @Test
    @DisplayName("Ralph 状态冲突错误保留错误码")
    void ralphLoopStateConflicts() {
        var tool = new RalphLoopTool();
        tool.start("goal");

        var running = assertThrows(RalphLoopError.class, () -> tool.start("other"));
        assertEquals(RalphLoopError.ALREADY_RUNNING, running.errorCode());

        var idle = new RalphLoopTool();
        var notRunning = assertThrows(RalphLoopError.class, idle::incrementRound);
        assertEquals(RalphLoopError.NOT_RUNNING, notRunning.errorCode());

        var invalid = assertThrows(ToolArgsError.class, () -> new RalphLoopTool().start(" "));
        assertEquals("INVALID_ARGS", invalid.errorCode());
    }

    @Test
    @DisplayName("MCP 重连耗尽错误保留错误码")
    void mcpReconnectExhausted() {
        var error = new McpReconnectExhaustedException("server-a", 3, new IllegalStateException("down"));

        assertEquals(McpReconnectExhaustedException.RECONNECT_EXHAUSTED, error.errorCode());
        assertEquals("McpReconnectExhaustedException", error.errorName());
        assertEquals("server-a", error.serverName());
        assertEquals(3, error.attempts());
    }

    @Test
    @DisplayName("Hook 拦截失败信息使用 HookError 名称与码")
    void hookBlockedFailure() {
        var failure = HookError.blocked().toFailure();

        assertEquals("HookError", failure.info().name());
        assertEquals(HookError.HOOK_BLOCKED, failure.info().code());
    }

    @Test
    @DisplayName("CUA 端口异常包装为 ComputerUseError")
    void computerUseExecutionFailure() {
        var tool = new ComputerUseTool(new IComputerUsePort() {
            @Override public byte[] screenshot() { throw new IllegalStateException("boom"); }
            @Override public void mouseClick(int x, int y) { throw new IllegalStateException("boom"); }
            @Override public void mouseMove(int x, int y) { throw new IllegalStateException("boom"); }
            @Override public void keyType(String text) { throw new IllegalStateException("boom"); }
            @Override public void keyPress(String key) { throw new IllegalStateException("boom"); }
        });

        var error = assertThrows(ComputerUseError.class, tool::screenshot);

        assertEquals(ComputerUseError.EXECUTION_FAILED, error.errorCode());
        assertEquals("ComputerUseError", error.errorName());
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    @Test
    @DisplayName("CUA 参数错误统一为 INVALID_ARGS")
    void computerUseInvalidArgs() {
        var tool = new ComputerUseTool(new IComputerUsePort() {
            @Override public byte[] screenshot() { return new byte[0]; }
            @Override public void mouseClick(int x, int y) { }
            @Override public void mouseMove(int x, int y) { }
            @Override public void keyType(String text) { }
            @Override public void keyPress(String key) { }
        });

        var error = assertThrows(ToolArgsError.class, () -> tool.mouseClick(-1, 0));
        assertEquals("INVALID_ARGS", error.errorCode());
    }

    @Test
    @DisplayName("浏览器端口异常包装为 BrowserUseError")
    void browserExecutionFailure() {
        var tool = new BrowserUseTool(new IBrowserUsePort() {
            @Override public void navigate(String url) { throw new IllegalStateException("boom"); }
            @Override public void click(String selector) { }
            @Override public void input(String selector, String text) { }
            @Override public String screenshot() { return null; }
            @Override public String getText(String selector) { return null; }
        });

        var navigateError = assertThrows(BrowserUseError.class,
                () -> tool.navigate("https://example.com"));
        assertEquals(BrowserUseError.EXECUTION_FAILED, navigateError.errorCode());

        var empty = assertThrows(BrowserUseError.class, tool::screenshot);
        assertEquals(BrowserUseError.EMPTY_RESULT, empty.errorCode());
    }

    @Test
    @DisplayName("浏览器参数错误统一为 INVALID_ARGS")
    void browserInvalidArgs() {
        var tool = new BrowserUseTool(new IBrowserUsePort() {
            @Override public void navigate(String url) { }
            @Override public void click(String selector) { }
            @Override public void input(String selector, String text) { }
            @Override public String screenshot() { return ""; }
            @Override public String getText(String selector) { return null; }
        });

        var error = assertThrows(ToolArgsError.class, () -> tool.navigate("ftp://example.com"));
        assertEquals("INVALID_ARGS", error.errorCode());
    }
}
