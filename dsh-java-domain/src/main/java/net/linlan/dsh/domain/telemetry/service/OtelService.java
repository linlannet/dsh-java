package net.linlan.dsh.domain.telemetry.service;

import net.linlan.dsh.domain.session.telemetry.model.valobj.OtlpBuffer;
import net.linlan.dsh.domain.telemetry.adapter.port.IOtelExporterPort;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class OtelService {

    private final OtlpBuffer buffer;
    private final IOtelExporterPort exporter;
    private final List<Span> activeSpans = new ArrayList<>();
    private boolean started = false;

    public OtelService(OtlpBuffer buffer, IOtelExporterPort exporter) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
    }

    public OtelService(IOtelExporterPort exporter) {
        this(new OtlpBuffer(), exporter);
    }

    public void start() {
        started = true;
    }

    public boolean isStarted() {
        return started;
    }

    public Span startSpan(String name) {
        Objects.requireNonNull(name, "name");
        if (!started) {
            throw new IllegalStateException("OtelService not started");
        }
        Span span = new Span(name, UUID.randomUUID().toString(), Instant.now());
        activeSpans.add(span);
        byte[] encoded = ("span:start:" + name + ":" + span.spanId()).getBytes(StandardCharsets.UTF_8);
        buffer.offer(encoded);
        return span;
    }

    public void exportOtlp(byte[] signals) {
        Objects.requireNonNull(signals, "signals");
        if (!started) {
            throw new IllegalStateException("OtelService not started");
        }
        buffer.offer(signals);
        exporter.export(signals);
    }

    public OtlpBuffer buffer() {
        return buffer;
    }

    public List<Span> activeSpans() {
        return List.copyOf(activeSpans);
    }

    public void endSpan(Span span) {
        Objects.requireNonNull(span, "span");
        byte[] encoded = ("span:end:" + span.name() + ":" + span.spanId()).getBytes(StandardCharsets.UTF_8);
        buffer.offer(encoded);
        activeSpans.remove(span);
    }

    public static final class Span {
        private final String name;
        private final String spanId;
        private final Instant startedAt;

        public Span(String name, String spanId, Instant startedAt) {
            this.name = name;
            this.spanId = spanId;
            this.startedAt = startedAt;
        }

        public String name() { return name; }
        public String spanId() { return spanId; }
        public Instant startedAt() { return startedAt; }
    }
}
