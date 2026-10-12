package net.linlan.dsh.domain.session.telemetry.service;

import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.telemetry.model.valobj.OtlpBuffer;

import java.nio.charset.StandardCharsets;

public final class SessionTelemetryExporter {

    private final OtlpBuffer buffer;

    public SessionTelemetryExporter() {
        this(new OtlpBuffer());
    }

    public SessionTelemetryExporter(OtlpBuffer buffer) {
        this.buffer = buffer;
    }

    public void export(SessionEvent event) {
        byte[] encoded = encode(event);
        buffer.offer(encoded);
    }

    public OtlpBuffer buffer() {
        return buffer;
    }

    private byte[] encode(SessionEvent event) {
        String wire = event.type().wireName() + ":" + event.seq() + "@" + event.time();
        return wire.getBytes(StandardCharsets.UTF_8);
    }
}
