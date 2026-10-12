package net.linlan.dsh.domain.telemetry.service;

import net.linlan.dsh.domain.session.telemetry.model.valobj.OtlpBuffer;
import net.linlan.dsh.domain.telemetry.adapter.port.IOtelExporterPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OtelService OTLP 遥测服务")
class OtelServiceTest {

    static final class CapturingExporter implements IOtelExporterPort {
        final List<byte[]> exported = new ArrayList<>();

        @Override
        public void export(byte[] data) {
            exported.add(data);
        }
    }

    @Test
    @DisplayName("start 后 isStarted 返回 true")
    void startSetsStartedFlag() {
        var exporter = new CapturingExporter();
        var service = new OtelService(exporter);
        assertFalse(service.isStarted());
        service.start();
        assertTrue(service.isStarted());
    }

    @Test
    @DisplayName("startSpan 返回非空 Span 并记录到 activeSpans")
    void startSpanReturnsAndRecordsSpan() {
        var exporter = new CapturingExporter();
        var service = new OtelService(exporter);
        service.start();
        var span = service.startSpan("tool.exec");
        assertNotNull(span);
        assertEquals("tool.exec", span.name());
        assertNotNull(span.spanId());
        assertNotNull(span.startedAt());
        assertEquals(1, service.activeSpans().size());
    }

    @Test
    @DisplayName("未 start 时 startSpan 抛出 IllegalStateException")
    void startSpanBeforeStartThrows() {
        var exporter = new CapturingExporter();
        var service = new OtelService(exporter);
        assertThrows(IllegalStateException.class, () -> service.startSpan("x"));
    }

    @Test
    @DisplayName("exportOtlp 将信号写入 buffer 并调用 exporter")
    void exportOtlpWritesBufferAndInvokesExporter() {
        var exporter = new CapturingExporter();
        var buffer = new OtlpBuffer(4096);
        var service = new OtelService(buffer, exporter);
        service.start();
        byte[] signals = "metrics-data".getBytes(StandardCharsets.UTF_8);
        service.exportOtlp(signals);
        assertEquals(1, buffer.size());
        assertEquals(1, exporter.exported.size());
        assertArrayEquals(signals, exporter.exported.get(0));
    }

    @Test
    @DisplayName("未 start 时 exportOtlp 抛出 IllegalStateException")
    void exportOtlpBeforeStartThrows() {
        var exporter = new CapturingExporter();
        var service = new OtelService(exporter);
        assertThrows(IllegalStateException.class, () -> service.exportOtlp(new byte[]{1}));
    }

    @Test
    @DisplayName("endSpan 从 activeSpans 移除并写入 buffer")
    void endSpanRemovesFromActiveAndBuffers() {
        var exporter = new CapturingExporter();
        var service = new OtelService(exporter);
        service.start();
        var span = service.startSpan("op");
        assertEquals(1, service.activeSpans().size());
        service.endSpan(span);
        assertEquals(0, service.activeSpans().size());
        assertTrue(service.buffer().size() >= 2);
    }

    @Test
    @DisplayName("自定义 buffer 注入后复用同一实例")
    void customBufferInjected() {
        var exporter = new CapturingExporter();
        var buffer = new OtlpBuffer(2048);
        var service = new OtelService(buffer, exporter);
        assertSame(buffer, service.buffer());
    }

    @Test
    @DisplayName("null 参数抛出 NullPointerException")
    void nullArgumentsRejected() {
        var exporter = new CapturingExporter();
        var service = new OtelService(exporter);
        service.start();
        assertThrows(NullPointerException.class, () -> service.startSpan(null));
        assertThrows(NullPointerException.class, () -> service.exportOtlp(null));
        assertThrows(NullPointerException.class, () -> service.endSpan(null));
    }
}
