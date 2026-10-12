package net.linlan.dsh.infrastructure.adapter.plugin;

import net.linlan.dsh.domain.plugin.registry.adapter.port.IRemoteInventoryPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

public final class HttpRemoteInventory implements IRemoteInventoryPort {

    private final HttpClient client;
    private final String baseUrl;

    public HttpRemoteInventory(String baseUrl) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build(), baseUrl);
    }

    public HttpRemoteInventory(HttpClient client, String baseUrl) {
        this.client = Objects.requireNonNull(client, "client");
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    @Override
    public List<PluginInfo> listPlugins() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/plugins"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return List.of();
            }
            return parseList(response.body());
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public Optional<PluginInfo> getPlugin(String id) {
        Objects.requireNonNull(id, "id");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/plugins/" + id))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return Optional.of(parseOne(response.body()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private List<PluginInfo> parseList(String body) {
        List<PluginInfo> result = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return result;
        }
        String[] entries = body.split("\\n");
        for (String entry : entries) {
            entry = entry.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            String[] parts = entry.split("\\|", -1);
            if (parts.length < 5) {
                continue;
            }
            result.add(new PluginInfo(parts[0], parts[1], parts[2], parts[3], parts[4]));
        }
        return result;
    }

    private PluginInfo parseOne(String body) {
        String[] parts = body.split("\\|", -1);
        if (parts.length < 5) {
            return new PluginInfo("", "", "", "", "");
        }
        return new PluginInfo(parts[0], parts[1], parts[2], parts[3], parts[4]);
    }
}
