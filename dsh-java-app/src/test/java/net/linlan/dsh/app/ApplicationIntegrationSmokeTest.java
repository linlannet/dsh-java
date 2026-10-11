package net.linlan.dsh.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationIntegrationSmokeTest {

    private ConfigurableApplicationContext context;
    private HttpClient client;
    private String baseUrl;
    private java.nio.file.Path tempDirectory;

    @BeforeAll
    void startApplication() {
        try {
            tempDirectory = Files.createTempDirectory("dsh-smoke");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to create test workspace", e);
        }
        String tempPath = tempDirectory.toString();
        context = new SpringApplicationBuilder(Application.class)
                .run(
                        "--server.port=0",
                        "--spring.profiles.active=standalone",
                        "--spring.sql.init.mode=always",
                        "--spring.datasource.url=jdbc:h2:mem:smoke;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                        "--harness.workspace.root=" + tempPath,
                        "--harness.storage.root=" + tempDirectory.resolve("storage"),
                        "--harness.agent.default-cwd=" + tempPath,
                        "--harness.terminal.default-cwd=" + tempPath
                );
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        baseUrl = "http://127.0.0.1:" + port;
    }

    @Test
    void exposesCoreConsoleAndQueryEndpoints() throws Exception {
        assertEndpointOk("/", "DeepSeek Harness");
        assertEndpointOk("/api/harness/config/effective", "\"00000\"");
        assertEndpointOk("/api/harness/plugins", "\"00000\"");
        assertEndpointOk("/api/harness/settings/models", "\"00000\"");
        assertEndpointOk("/api/agent/workspaces", "\"00000\"");
    }

    @AfterAll
    void stopApplication() {
        if (context != null) {
            context.close();
        }
    }

    private void assertEndpointOk(String path, String expectedContent) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), path);
        assertTrue(response.body().contains(expectedContent), path + " -> " + response.body());
    }
}
