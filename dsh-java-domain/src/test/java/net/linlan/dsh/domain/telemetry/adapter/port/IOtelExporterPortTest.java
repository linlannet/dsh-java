package net.linlan.dsh.domain.telemetry.adapter.port;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("T2.022 IOtelExporterPort 端口契约")
class IOtelExporterPortTest {

    @Test
    @DisplayName("export(byte[]) 接受字节数组并可通过实现类调用")
    void exportAcceptsByteArray() {
        IOtelExporterPort port = new RecordingPort();
        byte[] payload = new byte[] {1, 2, 3};
        port.export(payload);
        assertArrayEquals(payload, ((RecordingPort) port).lastExported);
    }

    @Test
    @DisplayName("export 空字节数组不抛异常")
    void exportEmptyByteArray() {
        IOtelExporterPort port = new RecordingPort();
        port.export(new byte[0]);
        assertEquals(0, ((RecordingPort) port).lastExported.length);
    }

    @Test
    @DisplayName("实现类实例是 IOtelExporterPort 子类型")
    void implementationIsPortSubtype() {
        IOtelExporterPort port = new RecordingPort();
        assertNotNull(port);
        assert IOtelExporterPort.class.isInstance(port);
    }

    static final class RecordingPort implements IOtelExporterPort {
        volatile byte[] lastExported;

        @Override
        public void export(byte[] data) {
            this.lastExported = data;
        }
    }
}
