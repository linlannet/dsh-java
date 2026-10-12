package net.linlan.dsh.domain.tool.ssh;

import net.linlan.dsh.domain.tool.ssh.adapter.port.ISshPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SshConnection SSH 连接")
class SshConnectionTest {

    @Test
    @DisplayName("构造合法连接信息成功")
    void constructValidConnection() {
        var conn = new SshConnection("host.example.com", 22, "user", "/key/path");
        assertEquals("host.example.com", conn.host());
        assertEquals(22, conn.port());
        assertEquals("user", conn.username());
        assertEquals("/key/path", conn.keyPath());
    }

    @Test
    @DisplayName("blank host 抛出异常")
    void blankHostThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SshConnection("", 22, "user", "/key"));
    }

    @Test
    @DisplayName("非法端口抛出异常")
    void invalidPortThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SshConnection("host", 0, "user", "/key"));
        assertThrows(IllegalArgumentException.class, () -> new SshConnection("host", 70000, "user", "/key"));
    }

    @Test
    @DisplayName("blank username 抛出异常")
    void blankUsernameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SshConnection("host", 22, "", "/key"));
    }

    @Test
    @DisplayName("of 使用默认端口 22")
    void ofUsesDefaultPort() {
        var conn = SshConnection.of("host", "user", "/key");
        assertEquals(22, conn.port());
    }

    @Test
    @DisplayName("toCommandLine 构建默认端口命令")
    void toCommandLineDefaultPort() {
        var conn = SshConnection.of("host", "user", "/key");
        String cmd = conn.toCommandLine();
        assertTrue(cmd.contains("ssh"));
        assertTrue(cmd.contains("user@host"));
        assertTrue(cmd.contains("-i /key"));
        assertFalse(cmd.contains("-p 22"));
    }

    @Test
    @DisplayName("toCommandLine 非默认端口包含 -p")
    void toCommandLineCustomPort() {
        var conn = new SshConnection("host", 2222, "user", null);
        String cmd = conn.toCommandLine();
        assertTrue(cmd.contains("-p 2222"));
    }

    @Test
    @DisplayName("toCommandLine null keyPath 不包含 -i")
    void toCommandLineNullKeyPath() {
        var conn = new SshConnection("host", 22, "user", null);
        String cmd = conn.toCommandLine();
        assertFalse(cmd.contains("-i"));
    }

    @Test
    @DisplayName("connect 委托端口返回 true")
    void connectDelegatesToPort() {
        var stub = new StubSshPort(true);
        var conn = SshConnection.of("host", "user", "/key");
        assertTrue(conn.connect(stub));
        assertTrue(stub.connectCalled);
    }

    @Test
    @DisplayName("connect null 端口返回 false")
    void connectNullPortReturnsFalse() {
        var conn = SshConnection.of("host", "user", "/key");
        assertFalse(conn.connect(null));
    }

    @Test
    @DisplayName("disconnect 委托端口")
    void disconnectDelegatesToPort() {
        var stub = new StubSshPort(true);
        var conn = SshConnection.of("host", "user", "/key");
        conn.disconnect(stub);
        assertTrue(stub.disconnectCalled);
    }

    @Test
    @DisplayName("disconnect null 端口无效果")
    void disconnectNullPortNoOp() {
        var conn = SshConnection.of("host", "user", "/key");
        conn.disconnect(null);
    }

    @Test
    @DisplayName("executeCommand 委托端口")
    void executeCommandDelegatesToPort() {
        var stub = new StubSshPort(true);
        var conn = SshConnection.of("host", "user", "/key");
        var result = conn.executeCommand(stub, "ls");
        assertEquals(0, result.exitCode());
        assertEquals("stub-output", result.stdout());
    }

    @Test
    @DisplayName("executeCommand null 端口抛出异常")
    void executeCommandNullPortThrows() {
        var conn = SshConnection.of("host", "user", "/key");
        assertThrows(IllegalArgumentException.class, () -> conn.executeCommand(null, "ls"));
    }

    static final class StubSshPort implements ISshPort {
        boolean connectCalled = false;
        boolean disconnectCalled = false;
        final boolean connectResult;

        StubSshPort(boolean connectResult) {
            this.connectResult = connectResult;
        }

        @Override
        public boolean connect() {
            connectCalled = true;
            return connectResult;
        }

        @Override
        public void disconnect() {
            disconnectCalled = true;
        }

        @Override
        public SshCommandResult execute(String command) {
            return new SshCommandResult("stub-output", "", 0);
        }

        @Override
        public String readFile(String remotePath) {
            return "stub-file-content";
        }

        @Override
        public boolean writeFile(String remotePath, String content) {
            return true;
        }
    }
}
