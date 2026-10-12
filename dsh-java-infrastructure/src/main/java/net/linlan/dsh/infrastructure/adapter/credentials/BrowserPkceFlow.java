package net.linlan.dsh.infrastructure.adapter.credentials;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.linlan.dsh.domain.credentials.pkce.PkceAuthorizer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BrowserPkceFlow implements PkceAuthorizer.PkceFlowPort {

    private final String authorizationEndpoint;
    private final String tokenEndpoint;
    private final int callbackPort;
    private final int timeoutSeconds;

    public BrowserPkceFlow(String authorizationEndpoint, String tokenEndpoint) {
        this(authorizationEndpoint, tokenEndpoint, 8765, 120);
    }

    public BrowserPkceFlow(String authorizationEndpoint, String tokenEndpoint, int callbackPort, int timeoutSeconds) {
        this.authorizationEndpoint = authorizationEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.callbackPort = callbackPort;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String exchange(String clientId, String redirectUri, String codeVerifier, String codeChallenge) {
        String state = java.util.UUID.randomUUID().toString();
        String authUrl = buildAuthorizationUrl(clientId, redirectUri, codeChallenge, state);

        openBrowser(authUrl);

        String authorizationCode = waitForCallback(state);

        return requestToken(clientId, redirectUri, codeVerifier, authorizationCode);
    }

    public String buildAuthorizationUrl(String clientId, String redirectUri, String codeChallenge, String state) {
        return authorizationEndpoint + "?" +
                "response_type=code" +
                "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&code_challenge=" + URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8) +
                "&code_challenge_method=S256" +
                "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
    }

    public void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to open browser: " + url, e);
        }
    }

    public String waitForCallback(String expectedState) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> codeRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(callbackPort), 0);
            server.createContext("/", new CallbackHandler(expectedState, codeRef, errorRef, latch));
            server.start();

            try {
                if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                    server.stop(0);
                    throw new RuntimeException("Timed out waiting for authorization callback");
                }
            } finally {
                server.stop(0);
            }

            if (errorRef.get() != null) {
                throw new RuntimeException("Authorization error: " + errorRef.get());
            }
            return codeRef.get();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start callback server", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for callback", e);
        }
    }

    public String requestToken(String clientId, String redirectUri, String codeVerifier, String authorizationCode) {
        String body = "grant_type=authorization_code" +
                "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&code=" + URLEncoder.encode(authorizationCode, StandardCharsets.UTF_8) +
                "&code_verifier=" + URLEncoder.encode(codeVerifier, StandardCharsets.UTF_8);

        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();
            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("Failed to request token", e);
        }
    }

    static class CallbackHandler implements HttpHandler {
        private final String expectedState;
        private final AtomicReference<String> codeRef;
        private final AtomicReference<String> errorRef;
        private final CountDownLatch latch;

        CallbackHandler(String expectedState, AtomicReference<String> codeRef,
                        AtomicReference<String> errorRef, CountDownLatch latch) {
            this.expectedState = expectedState;
            this.codeRef = codeRef;
            this.errorRef = errorRef;
            this.latch = latch;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String code = extractParam(query, "code");
            String state = extractParam(query, "state");
            String error = extractParam(query, "error");

            String response;
            if (error != null) {
                errorRef.set(error);
                response = "Authorization failed: " + error;
            } else if (state == null || !state.equals(expectedState)) {
                errorRef.set("state mismatch");
                response = "State mismatch";
            } else if (code != null) {
                codeRef.set(code);
                response = "Authorization successful. You can close this window.";
            } else {
                errorRef.set("no code in response");
                response = "No authorization code received";
            }

            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (var os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
            latch.countDown();
        }

        private String extractParam(String query, String name) {
            if (query == null) return null;
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv[0].equals(name) && kv.length == 2) {
                    return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
            return null;
        }
    }
}
