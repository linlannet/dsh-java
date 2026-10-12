package net.linlan.dsh.infrastructure.adapter.otel;

import net.linlan.dsh.domain.telemetry.adapter.port.IOtelExporterPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "dsh.otel.enabled", havingValue = "true")
public class OtlpHttpExporter implements IOtelExporterPort {

    private static final Logger log = LoggerFactory.getLogger(OtlpHttpExporter.class);

    private final HttpClient httpClient;
    private final String endpoint;

    @Autowired
    public OtlpHttpExporter(@Value("${dsh.otel.exporter.otlp-endpoint:http://127.0.0.1:4318/v1/traces}") String endpoint) {
        this(endpoint, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public OtlpHttpExporter(String endpoint, HttpClient httpClient) {
        this.endpoint = endpoint;
        this.httpClient = httpClient;
    }

    @Override
    public void export(byte[] data) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/x-protobuf")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(data))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                log.warn("OTLP export returned status {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("OTLP export failed: {}", e.getMessage());
        }
    }

    public String endpoint() {
        return endpoint;
    }
}
