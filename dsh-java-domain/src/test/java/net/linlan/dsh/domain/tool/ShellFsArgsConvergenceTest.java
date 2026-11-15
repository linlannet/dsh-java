package net.linlan.dsh.domain.tool;

import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.tool.fs.StrReplaceEditorTool;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import net.linlan.dsh.domain.tool.shell.ShellExecuteTool;
import net.linlan.dsh.domain.tool.shell.ShellResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Shell/FS 参数错误统一为 INVALID_ARGS")
class ShellFsArgsConvergenceTest {

    @Test
    @DisplayName("Shell 缺少 command 返回 ToolArgsError/INVALID_ARGS")
    void shellMissingCommand() {
        var tool = new ShellExecuteTool(
                (command, cwd, timeout) -> new ShellResult("", "", 0, 0), ".");

        assertArgsFailure(tool.execute(Map.of("command", " "), null).join());
        assertArgsFailure(tool.execute(Map.of(), null).join());
    }

    @Test
    @DisplayName("FS 缺少 command 返回 ToolArgsError/INVALID_ARGS")
    void fsEditorMissingCommand() {
        var tool = new StrReplaceEditorTool(new NoopFsPort());

        assertArgsFailure(tool.execute(Map.of(), null).join());
    }

    private static void assertArgsFailure(ToolExecutionResult result) {
        var failure = assertInstanceOf(ToolExecutionResult.Failure.class, result);

        assertEquals("ToolArgsError", failure.error().info().name());
        assertEquals("INVALID_ARGS", failure.error().info().code());
        assertTrue(failure.error().message().startsWith("invalid arguments: "));
    }

    private static final class NoopFsPort implements IFsPort {

        @Override
        public String read(String path, int offset, int limit) {
            return "";
        }

        @Override
        public void write(String path, String content) {
        }

        @Override
        public List<String> search(String directory, String pattern, int maxResults) {
            return List.of();
        }

        @Override
        public boolean exists(String path) {
            return false;
        }

        @Override
        public List<DirEntry> listDir(String directory) {
            return List.of();
        }

        @Override
        public List<String> grep(String directory, String pattern, String include,
                                 boolean isRegex, int maxResults) {
            return List.of();
        }

        @Override
        public byte[] readBytes(String path) {
            return new byte[0];
        }
    }
}
