package net.linlan.dsh.infrastructure.digitalhuman;

import net.linlan.dsh.infrastructure.dao.IDigitalHumanDao;
import net.linlan.dsh.infrastructure.dao.IDigitalHumanEndpointDao;
import net.linlan.dsh.infrastructure.dao.po.DigitalHumanEndpointPO;
import net.linlan.dsh.infrastructure.dao.po.DigitalHumanPO;
import net.linlan.dsh.api.port.IDigitalHumanService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DigitalHumanService implements IDigitalHumanService {
    private static final Logger log = LoggerFactory.getLogger(DigitalHumanService.class);
    private static final String PROTOCOL = "dsh.v1";
    private final IDigitalHumanDao humanDao;
    private final IDigitalHumanEndpointDao endpointDao;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DigitalHumanService(IDigitalHumanDao humanDao, IDigitalHumanEndpointDao endpointDao) {
        this.humanDao = humanDao;
        this.endpointDao = endpointDao;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public List<Map<String, Object>> list() {
        ArrayList<Map<String, Object>> views = new ArrayList<Map<String, Object>>();
        for (DigitalHumanPO human : this.humanDao.listAll()) {
            views.add(this.toView(human, this.endpointDao.findByHumanId(human.id()).orElse(null)));
        }
        return views;
    }

    public Map<String, Object> get(String id) {
        DigitalHumanPO human = this.humanDao.findById(id).orElseThrow(() -> new IllegalArgumentException("\u6570\u5b57\u4eba\u4e0d\u5b58\u5728\uff1a" + id));
        return this.toView(human, this.endpointDao.findByHumanId(id).orElse(null));
    }

    public Map<String, Object> create(Map<String, Object> body) {
        String id = "dh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant now = Instant.now();
        DigitalHumanPO human = new DigitalHumanPO(id, this.requireText(body.get("displayName"), "\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a"), this.text(body.get("avatarRef"), "\ud83e\udd16"), this.requireText(body.get("purpose"), "\u7528\u9014\u63cf\u8ff0\u4e0d\u80fd\u4e3a\u7a7a"), this.joinTags(body.get("roleTags")), this.text(body.get("themeColor"), "#4160f0"), this.text(body.get("approvalPolicy"), "WRITE_REQUIRES_APPROVAL"), this.clampInt(body.get("concurrencyLimit"), 1, 8, 1), this.normalizeProjectPath(body.get("projectPath")), now, now);
        this.humanDao.insert(human);
        this.upsertEndpoint(id, body, now);
        return this.get(id);
    }

    public Map<String, Object> update(String id, Map<String, Object> body) {
        DigitalHumanPO existing = this.humanDao.findById(id).orElseThrow(() -> new IllegalArgumentException("\u6570\u5b57\u4eba\u4e0d\u5b58\u5728\uff1a" + id));
        DigitalHumanPO merged = new DigitalHumanPO(id, this.text(body.get("displayName"), existing.displayName()), this.text(body.get("avatarRef"), existing.avatarRef()), this.text(body.get("purpose"), existing.purpose()), body.containsKey("roleTags") ? this.joinTags(body.get("roleTags")) : existing.roleTags(), this.text(body.get("themeColor"), existing.themeColor()), this.text(body.get("approvalPolicy"), existing.approvalPolicy()), body.containsKey("concurrencyLimit") ? this.clampInt(body.get("concurrencyLimit"), 1, 8, existing.concurrencyLimit()) : existing.concurrencyLimit().intValue(), body.containsKey("projectPath") ? this.normalizeProjectPath(body.get("projectPath")) : existing.projectPath(), existing.createdAt(), Instant.now());
        this.humanDao.update(merged);
        if (body.get("endpoint") instanceof Map) {
            this.upsertEndpoint(id, body, Instant.now());
        }
        return this.get(id);
    }

    public void delete(String id) {
        this.endpointDao.deleteByHumanId(id);
        this.humanDao.deleteById(id);
    }

    private void upsertEndpoint(String humanId, Map<String, Object> body, Instant now) {
        Integer n;
        Object object = body.get("endpoint");
        if (!(object instanceof Map)) {
            return;
        }
        Map rawEndpoint = (Map)object;
        Map endpoint = rawEndpoint;
        String type = this.text(endpoint.get("type"), "local-dsh");
        String baseUrl = this.normalizeBaseUrl(this.text(endpoint.get("baseUrl"), null));
        String string = "ep_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String string2 = this.text(endpoint.get("credentialRef"), null);
        String string3 = this.text(endpoint.get("protocolVersion"), PROTOCOL);
        String string4 = this.text(endpoint.get("healthState"), "local-dsh".equals(type) ? "online" : "unknown");
        Object v = endpoint.get("latencyMs");
        if (v instanceof Number) {
            Number number = (Number)v;
            n = number.intValue();
        } else {
            n = null;
        }
        DigitalHumanEndpointPO po = new DigitalHumanEndpointPO(string, humanId, type, baseUrl, string2, string3, null, string4, now, n);
        if (this.endpointDao.updateByHumanId(po) == 0) {
            this.endpointDao.insert(po);
        }
    }

    public Map<String, Object> discover(String baseUrl, String endpointType, String credentialRef, String token) {
        String normalized = this.normalizeBaseUrl(baseUrl);
        String type = endpointType == null || endpointType.isBlank() ? "remote-dsh" : endpointType;
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        if (normalized == null) {
            result.put("reachable", false);
            result.put("error", Map.of("kind", "network", "message", "\u8bf7\u586b\u5199\u670d\u52a1\u5730\u5740"));
            return result;
        }
        if ("a2a".equals(type)) {
            return this.discoverA2a(normalized, token, result);
        }
        return this.discoverDsh(normalized, token, result);
    }

    private Map<String, Object> discoverDsh(String normalized, String token, Map<String, Object> result) {
        long startedAt = System.currentTimeMillis();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(normalized + "/.well-known/dsh-agent-card")).timeout(Duration.ofSeconds(10L)).GET();
            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token.trim());
            }
            HttpResponse<String> response = this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - startedAt;
            result.put("latencyMs", latency);
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                result.put("reachable", false);
                result.put("error", Map.of("kind", "unauthorized", "message", "\u51ed\u636e\u65e0\u6548\u6216\u6743\u9650\u4e0d\u8db3"));
                return result;
            }
            if (response.statusCode() == 404) {
                result.put("reachable", false);
                result.put("error", Map.of("kind", "not-found", "message", "\u8be5\u670d\u52a1\u672a\u66b4\u9732 DSH \u80fd\u529b\u5361"));
                return result;
            }
            if (response.statusCode() != 200) {
                result.put("reachable", false);
                result.put("error", Map.of("kind", "network", "message", "HTTP " + response.statusCode()));
                return result;
            }
            JsonNode card = this.objectMapper.readTree(response.body());
            if (!card.hasNonNull("protocol")) {
                result.put("reachable", false);
                result.put("error", Map.of("kind", "incompatible", "message", "\u54cd\u5e94\u4e0d\u662f\u6709\u6548\u7684 DSH Agent Card"));
                return result;
            }
            LinkedHashMap<String, Object> suggested = new LinkedHashMap<String, Object>();
            suggested.put("displayName", card.path("displayName").asText(null));
            suggested.put("avatarRef", card.path("avatarUrl").asText(null));
            suggested.put("purpose", card.path("purpose").asText(null));
            ArrayList tags = new ArrayList();
            card.path("roleTags").forEach(node -> tags.add(node.asText()));
            suggested.put("roleTags", tags);
            suggested.put("approvalPolicy", card.path("approvalPolicy").asText("WRITE_REQUIRES_APPROVAL"));
            suggested.put("maxConcurrentTasks", card.path("maxConcurrentTasks").asInt(1));
            result.put("reachable", true);
            result.put("protocolVersion", card.path("protocol").asText());
            result.put("suggested", suggested);
            return result;
        }
        catch (HttpConnectTimeoutException e) {
            return this.unreachable(result, startedAt, "network", "\u8fde\u63a5\u8d85\u65f6");
        }
        catch (SSLException e) {
            return this.unreachable(result, startedAt, "tls", "TLS \u8bc1\u4e66\u6821\u9a8c\u5931\u8d25\uff1a" + e.getMessage());
        }
        catch (UnknownHostException e) {
            return this.unreachable(result, startedAt, "dns", "\u57df\u540d\u89e3\u6790\u5931\u8d25");
        }
        catch (Exception e) {
            log.warn("Agent card discover failed for {}: {}", (Object)normalized, (Object)e.getMessage());
            return this.unreachable(result, startedAt, "network", "\u8fde\u63a5\u5931\u8d25\uff1a" + e.getMessage());
        }
    }

    private Map<String, Object> discoverA2a(String normalized, String token, Map<String, Object> result) {
        long startedAt = System.currentTimeMillis();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(normalized + "/.well-known/agent.json")).timeout(Duration.ofSeconds(10L)).GET();
            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token.trim());
            }
            HttpResponse<String> response = this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - startedAt;
            result.put("latencyMs", latency);
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                result.put("reachable", false);
                result.put("error", Map.of("kind", "unauthorized", "message", "A2A \u51ed\u636e\u65e0\u6548\u6216\u6743\u9650\u4e0d\u8db3"));
                return result;
            }
            if (response.statusCode() == 404) {
                result.put("reachable", false);
                result.put("error", Map.of("kind", "not-found", "message", "\u8be5\u670d\u52a1\u672a\u66b4\u9732 A2A Agent Card"));
                return result;
            }
            if (response.statusCode() != 200) {
                result.put("reachable", false);
                result.put("error", Map.of("kind", "network", "message", "HTTP " + response.statusCode()));
                return result;
            }
            JsonNode card = this.objectMapper.readTree(response.body());
            if (!(card.hasNonNull("name") || card.hasNonNull("description") || card.has("skills"))) {
                result.put("reachable", false);
                result.put("error", Map.of("kind", "incompatible", "message", "\u54cd\u5e94\u4e0d\u662f\u6709\u6548\u7684 A2A Agent Card"));
                return result;
            }
            LinkedHashMap<String, Object> suggested = new LinkedHashMap<String, Object>();
            suggested.put("displayName", card.path("name").asText(card.path("displayName").asText("A2A Agent")));
            suggested.put("avatarRef", "\ud83e\udd1d");
            suggested.put("purpose", card.path("description").asText("\u901a\u8fc7 A2A \u534f\u8bae\u63a5\u5165\u7684\u5916\u90e8\u667a\u80fd\u4f53"));
            ArrayList tags = new ArrayList();
            card.path("skills").forEach(skill -> {
                if (skill.hasNonNull("name")) {
                    tags.add(skill.path("name").asText());
                }
                skill.path("tags").forEach(tag -> tags.add(tag.asText()));
            });
            suggested.put("roleTags", tags.stream().distinct().limit(8L).toList());
            suggested.put("approvalPolicy", "WRITE_REQUIRES_APPROVAL");
            suggested.put("maxConcurrentTasks", 1);
            result.put("reachable", true);
            result.put("protocolVersion", card.path("protocolVersion").asText("a2a.v1"));
            result.put("suggested", suggested);
            return result;
        }
        catch (HttpConnectTimeoutException e) {
            return this.unreachable(result, startedAt, "network", "\u8fde\u63a5\u8d85\u65f6");
        }
        catch (SSLException e) {
            return this.unreachable(result, startedAt, "tls", "TLS \u8bc1\u4e66\u6821\u9a8c\u5931\u8d25\uff1a" + e.getMessage());
        }
        catch (UnknownHostException e) {
            return this.unreachable(result, startedAt, "dns", "\u57df\u540d\u89e3\u6790\u5931\u8d25");
        }
        catch (Exception e) {
            log.warn("A2A agent card discover failed for {}: {}", (Object)normalized, (Object)e.getMessage());
            return this.unreachable(result, startedAt, "network", "\u8fde\u63a5\u5931\u8d25\uff1a" + e.getMessage());
        }
    }

    private Map<String, Object> unreachable(Map<String, Object> result, long startedAt, String kind, String message) {
        result.put("reachable", false);
        result.put("latencyMs", System.currentTimeMillis() - startedAt);
        result.put("error", Map.of("kind", kind, "message", message));
        return result;
    }

    public Map<String, Object> healthCheck(String id, String token) {
        String state;
        DigitalHumanPO human = this.humanDao.findById(id).orElseThrow(() -> new IllegalArgumentException("\u6570\u5b57\u4eba\u4e0d\u5b58\u5728\uff1a" + id));
        DigitalHumanEndpointPO endpoint = this.endpointDao.findByHumanId(id).orElse(null);
        Integer latency = null;
        if (endpoint == null || "local-dsh".equals(endpoint.endpointType())) {
            state = "online";
        } else if (endpoint.baseUrl() == null) {
            state = "unknown";
        } else {
            Integer n;
            Map err;
            Object object;
            Map<String, Object> probe = this.discover(endpoint.baseUrl(), endpoint.endpointType(), endpoint.credentialRef(), token);
            state = Boolean.TRUE.equals(probe.get("reachable")) ? "online" : ((object = probe.get("error")) instanceof Map && "unauthorized".equals((err = (Map)object).get("kind")) ? "unauthorized" : "offline");
            object = probe.get("latencyMs");
            if (object instanceof Number) {
                Number number = (Number)object;
                n = number.intValue();
            } else {
                n = null;
            }
            latency = n;
        }
        this.endpointDao.updateHealth(id, state, Instant.now(), latency);
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("healthState", state);
        result.put("latencyMs", latency);
        return result;
    }

    private Map<String, Object> toView(DigitalHumanPO human, DigitalHumanEndpointPO endpoint) {
        LinkedHashMap<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("id", human.id());
        view.put("displayName", human.displayName());
        view.put("avatarRef", human.avatarRef());
        view.put("purpose", human.purpose());
        view.put("roleTags", human.roleTags() == null || human.roleTags().isBlank() ? List.of() : List.of(human.roleTags().split(",")));
        view.put("themeColor", human.themeColor());
        view.put("approvalPolicy", human.approvalPolicy());
        view.put("concurrencyLimit", human.concurrencyLimit());
        view.put("projectPath", human.projectPath());
        view.put("createdAt", human.createdAt() == null ? null : human.createdAt().toString());
        LinkedHashMap<String, Object> endpointView = new LinkedHashMap<String, Object>();
        endpointView.put("type", endpoint == null ? "local-dsh" : endpoint.endpointType());
        endpointView.put("baseUrl", endpoint == null ? null : endpoint.baseUrl());
        endpointView.put("credentialRef", endpoint == null ? null : endpoint.credentialRef());
        endpointView.put("protocolVersion", endpoint == null ? PROTOCOL : endpoint.protocolVersion());
        endpointView.put("healthState", endpoint == null ? "online" : endpoint.healthState());
        endpointView.put("lastCheckedAt", endpoint != null && endpoint.lastCheckedAt() != null ? endpoint.lastCheckedAt().toString() : null);
        endpointView.put("latencyMs", endpoint == null ? null : endpoint.latencyMs());
        view.put("endpoint", endpointView);
        return view;
    }

    private String requireText(Object value, String message) {
        String text = this.text(value, null);
        if (text == null) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private String text(Object value, String fallback) {
        String string;
        if (value instanceof String && !(string = (String)value).isBlank()) {
            return string.trim();
        }
        return fallback;
    }

    private String normalizeProjectPath(Object value) {
        String string;
        if (value instanceof String && !(string = (String)value).isBlank()) {
            return string.trim();
        }
        return null;
    }

    private String joinTags(Object value) {
        if (value instanceof List) {
            List list = (List)value;
            ArrayList<String> tags = new ArrayList<String>();
            for (Object item : list) {
                String tag;
                if (!(item instanceof String) || (tag = (String)item).isBlank()) continue;
                tags.add(tag.trim());
            }
            return String.join((CharSequence)",", tags);
        }
        return this.text(value, "");
    }

    private int clampInt(Object value, int min, int max, int fallback) {
        if (value instanceof Number) {
            Number number = (Number)value;
            return Math.max(min, Math.min(max, number.intValue()));
        }
        return fallback;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        return normalized.startsWith("http://") || normalized.startsWith("https://") ? normalized : null;
    }
}

