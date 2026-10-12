package net.linlan.dsh.domain.session.telemetry.service;

import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import net.linlan.dsh.domain.session.telemetry.model.valobj.OtlpBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionTelemetryExporter OTLP 遥测导出")
class SessionTelemetryExporterTest {

    @Test
    @DisplayName("export 事件后缓冲区 size 增加")
    void exportAddsToBuffer() {
        var exporter = new SessionTelemetryExporter();
        var event = SessionEventFactory.userMessage("hello");
        exporter.export(event);
        assertEquals(1, exporter.buffer().size());
        assertTrue(exporter.buffer().currentBytes() > 0);
    }

    @Test
    @DisplayName("自定义 buffer 注入后复用同一实例")
    void customBufferInjected() {
        var buffer = new OtlpBuffer(1024);
        var exporter = new SessionTelemetryExporter(buffer);
        assertSame(buffer, exporter.buffer());
    }

    @Test
    @DisplayName("多次 export 累积到缓冲区")
    void multipleExportsAccumulate() {
        var exporter = new SessionTelemetryExporter(new OtlpBuffer(4096));
        exporter.export(SessionEventFactory.userMessage("first"));
        exporter.export(SessionEventFactory.userMessage("second"));
        exporter.export(SessionEventFactory.userMessage("third"));
        assertEquals(3, exporter.buffer().size());
    }

    @Test
    @DisplayName("缓冲区超界时丢弃最旧事件")
    void bufferOverflowDropsOldest() {
        var buffer = new OtlpBuffer(20);
        var exporter = new SessionTelemetryExporter(buffer);
        exporter.export(SessionEventFactory.userMessage("AAAAAAAAAA"));
        exporter.export(SessionEventFactory.userMessage("BBBBBBBBBB"));
        exporter.export(SessionEventFactory.userMessage("CCCCCCCCCC"));
        assertTrue(buffer.droppedCount() >= 1);
        assertTrue(buffer.currentBytes() <= 20);
    }
}
