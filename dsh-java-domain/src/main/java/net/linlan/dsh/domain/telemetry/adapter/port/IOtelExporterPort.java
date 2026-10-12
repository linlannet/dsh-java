package net.linlan.dsh.domain.telemetry.adapter.port;

public interface IOtelExporterPort {
    void export(byte[] data);
}
