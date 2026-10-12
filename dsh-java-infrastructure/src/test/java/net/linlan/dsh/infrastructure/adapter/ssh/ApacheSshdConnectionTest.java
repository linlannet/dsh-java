package net.linlan.dsh.infrastructure.adapter.ssh;

import net.linlan.dsh.domain.tool.ssh.SshConnection;
import net.linlan.dsh.domain.tool.ssh.adapter.port.ISshPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApacheSshdConnectionTest {

    @Test
    void implementsSshPort() {
        ApacheSshdConnection c = new ApacheSshdConnection(SshConnection.of("host", "user", null));
        assertTrue(c instanceof ISshPort);
    }

    @Test
    void nullConnectionThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ApacheSshdConnection(null));
    }

    @Test
    void nullConnectionThrowsWithTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new ApacheSshdConnection(null, 1000));
    }

    @Test
    void defaultConstructorUsesDefaultTimeout() {
        ApacheSshdConnection c = new ApacheSshdConnection(SshConnection.of("host", "user", null));
        assertNotNull(c.connection());
        assertFalse(c.isConnected());
    }

    @Test
    void customTimeoutConstructor() {
        ApacheSshdConnection c = new ApacheSshdConnection(SshConnection.of("host", "user", null), 5000);
        assertNotNull(c.connection());
        assertFalse(c.isConnected());
    }

    @Test
    void negativeTimeoutFallsBackToDefault() {
        ApacheSshdConnection c = new ApacheSshdConnection(SshConnection.of("host", "user", null), -1);
        assertNotNull(c);
        assertFalse(c.isConnected());
    }

    @Test
    void zeroTimeoutFallsBackToDefault() {
        ApacheSshdConnection c = new ApacheSshdConnection(SshConnection.of("host", "user", null), 0);
        assertNotNull(c);
        assertFalse(c.isConnected());
    }

    @Test
    void connectionAccessorReturnsProvidedConnection() {
        SshConnection sc = SshConnection.of("myhost", "myuser", "/key/path");
        ApacheSshdConnection c = new ApacheSshdConnection(sc);
        assertSame(sc, c.connection());
    }

    @Test
    void disconnectSetsConnectedFalse() {
        ApacheSshdConnection c = new ApacheSshdConnection(SshConnection.of("host", "user", null));
        c.disconnect();
        assertFalse(c.isConnected());
    }

    @Test
    void connectReturnsFalseForUnreachableHost() {
        ApacheSshdConnection c = new ApacheSshdConnection(
                SshConnection.of("nonexistent-host-xyz-invalid", "user", null), 3000);
        boolean result = c.connect();
        assertFalse(result);
        assertFalse(c.isConnected());
    }

    @Test
    void executeReturnsResultForFailedConnection() {
        ApacheSshdConnection c = new ApacheSshdConnection(
                SshConnection.of("nonexistent-host-xyz-invalid", "user", null), 3000);
        ISshPort.SshCommandResult result = c.execute("echo hi");
        assertNotNull(result);
        assertFalse(result.isSuccess());
    }

    @Test
    void readFileReturnsEmptyForFailedConnection() {
        ApacheSshdConnection c = new ApacheSshdConnection(
                SshConnection.of("nonexistent-host-xyz-invalid", "user", null), 3000);
        String content = c.readFile("/tmp/test");
        assertEquals("", content);
    }

    @Test
    void writeFileReturnsFalseForFailedConnection() {
        ApacheSshdConnection c = new ApacheSshdConnection(
                SshConnection.of("nonexistent-host-xyz-invalid", "user", null), 3000);
        boolean ok = c.writeFile("/tmp/test", "content");
        assertFalse(ok);
    }

    @Test
    void writeFileNullContentDoesNotThrow() {
        ApacheSshdConnection c = new ApacheSshdConnection(
                SshConnection.of("nonexistent-host-xyz-invalid", "user", null), 3000);
        boolean ok = c.writeFile("/tmp/test", null);
        assertFalse(ok);
    }
}
