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
import java.util.Random;

@Component
@ConditionalOnProperty(name = "dsh.otel.enabled", havingValue = "true")
public class OtlpHttpExporter implements IOtelExporterPort {

    private static final Logger log = LoggerFactory.getLogger(OtlpHttpExporter.class);

    private final HttpClient httpClient;
    private final String endpoint;
    private final double sampleRate;
    private final int maxRetries;
    private final Duration retryBaseDelay;
    private final String authHeader;
    private final String authValue;
    private final Random random;

    @Autowired
    public OtlpHttpExporter(
            @Value("${dsh.otel.exporter.otlp-endpoint:http://127.0.0.1:4318/v1/traces}") String endpoint,
            @Value("${dsh.otel.exporter.sample-rate:1.0}") double sampleRate,
            @Value("${dsh.otel.exporter.max-retries:3}") int maxRetries,
            @Value("${dsh.otel.exporter.retry-base-delay-ms:100}") long retryBaseDelayMs,
            @Value("${dsh.otel.exporter.auth-header:}") String authHeader,
            @Value("${dsh.otel.exporter.auth-value:}") String authValue) {
        this(endpoint, sampleRate, maxRetries, Duration.ofMillis(retryBaseDelayMs), authHeader, authValue,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new Random());
    }

    public OtlpHttpExporter(String endpoint) {
        this(endpoint, 1.0, 3, Duration.ofMillis(100), null, null,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new Random());
    }

    public OtlpHttpExporter(String endpoint, HttpClient httpClient) {
        this(endpoint, 1.0, 0, Duration.ZERO, null, null, httpClient, new Random());
    }

    public OtlpHttpExporter(String endpoint, double sampleRate, int maxRetries, Duration retryBaseDelay,
                            String authHeader, String authValue) {
        this(endpoint, sampleRate, maxRetries, retryBaseDelay, authHeader, authValue,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new Random());
    }

    public OtlpHttpExporter(String endpoint, double sampleRate, int maxRetries, Duration retryBaseDelay,
                            String authHeader, String authValue, HttpClient httpClient, Random random) {
        this.endpoint = endpoint;
        this.sampleRate = Math.max(0.0, Math.min(1.0, sampleRate));
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBaseDelay = retryBaseDelay == null ? Duration.ZERO : retryBaseDelay;
        this.authHeader = (authHeader == null || authHeader.isBlank()) ? null : authHeader;
        this.authValue = (authValue == null || authValue.isBlank()) ? null : authValue;
        this.httpClient = httpClient;
        this.random = random;
    }

    @Override
    public void export(byte[] data) {
        if (sampleRate <= 0.0) {
            return;
        }
        if (sampleRate < 1.0 && random.nextDouble() >= sampleRate) {
            return;
        }
        sendWithRetry(data);
    }

    private void sendWithRetry(byte[] data) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/x-protobuf")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(data));
                if (authHeader != null && authValue != null) {
                    builder.header(authHeader, authValue);
                }
                HttpRequest request = builder.build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status < 400) {
                    return;
                }
                if (status < 500) {
                    log.warn("OTLP export returned client error status {}, not retrying", status);
                    return;
                }
                log.warn("OTLP export returned server error status {} on attempt {}/{}", status, attempt + 1, maxRetries + 1);
            } catch (Exception e) {
                log.warn("OTLP export failed on attempt {}/{}: {}", attempt + 1, maxRetries + 1, e.getMessage());
            }
            if (attempt < maxRetries) {
                try {
                    long delayMs = retryBaseDelay.toMillis() * (1L << attempt);
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    public String endpoint() {
        return endpoint;
    }

    public double sampleRate() {
        return sampleRate;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public String authHeader() {
        return authHeader;
    }
}
