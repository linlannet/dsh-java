package net.linlan.dsh.domain.boot.cmdline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DshCommandLine 命令行解析")
class DshCommandLineTest {

    @Test
    @DisplayName("--help 标记帮助请求")
    void helpFlag() {
        var cli = new DshCommandLine();
        cli.parse(new String[]{"--help"});
        assertTrue(cli.isHelpRequested());
        assertTrue(cli.helpText().contains("DeepSeek Harness Java"));
    }

    @Test
    @DisplayName("--dsh. 前缀 flag 归入 appFlags")
    void dshFlags() {
        var cli = new DshCommandLine();
        cli.parse(new String[]{"--dsh.profile=prod", "--dsh.debug"});
        assertEquals(List.of("--dsh.profile=prod", "--dsh.debug"), cli.appFlags());
        assertTrue(cli.passthroughArgs().isEmpty());
    }

    @Test
    @DisplayName("-- 分隔后参数原样透传")
    void separatorPassthrough() {
        var cli = new DshCommandLine();
        cli.parse(new String[]{"--dsh.profile=prod", "--", "serve", "--port", "8080"});
        assertEquals(List.of("--dsh.profile=prod"), cli.appFlags());
        assertEquals(List.of("serve", "--port", "8080"), cli.passthroughArgs());
    }

    @Test
    @DisplayName("非 dsh flag 直接归入透传")
    void nonDshFlagPassthrough() {
        var cli = new DshCommandLine();
        cli.parse(new String[]{"serve", "--port", "8080"});
        assertTrue(cli.appFlags().isEmpty());
        assertEquals(List.of("serve", "--port", "8080"), cli.passthroughArgs());
    }

    @Test
    @DisplayName("退出码可设置")
    void exitCode() {
        var cli = new DshCommandLine();
        cli.setExitCode(42);
        assertEquals(42, cli.exitCode());
    }
}
