package net.linlan.dsh.infrastructure.digitalhuman;

import net.linlan.dsh.domain.agent.contract.outbound.RuntimeApprovalGateway;
import net.linlan.dsh.domain.agent.service.run.RuntimeApprovalBroker;
import net.linlan.dsh.domain.tool.service.RuntimeApprovalGate;
import net.linlan.dsh.infrastructure.dao.IDigitalHumanDao;
import net.linlan.dsh.infrastructure.dao.IDigitalHumanEndpointDao;
import net.linlan.dsh.infrastructure.dao.po.DigitalHumanEndpointPO;
import net.linlan.dsh.infrastructure.digitalhuman.RemoteEndpointCredentialRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AggregatingRuntimeApprovalGateway
implements RuntimeApprovalGateway {
    private static final Logger log = LoggerFactory.getLogger(AggregatingRuntimeApprovalGateway.class);
    private static final String SUCCESS_CODE = "00000";
    private static final long REMOTE_CACHE_MS = 1000L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3L);
    private final RuntimeApprovalBroker local;
    private final IDigitalHumanEndpointDao endpointDao;
    private final IDigitalHumanDao humanDao;
    private final RemoteEndpointCredentialRegistry credentials;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2L)).build();
    private final ConcurrentMap<String, String> approvalOrigin = new ConcurrentHashMap<String, String>();
    private volatile List<RuntimeApprovalGateway.PendingApprovalView> remoteCache = List.of();
    private volatile long remoteCacheAt = 0L;

    public AggregatingRuntimeApprovalGateway(RuntimeApprovalBroker local, IDigitalHumanEndpointDao endpointDao, IDigitalHumanDao humanDao, RemoteEndpointCredentialRegistry credentials) {
        this.local = local;
        this.endpointDao = endpointDao;
        this.humanDao = humanDao;
        this.credentials = credentials;
    }

    public RuntimeApprovalGate.ApprovalVerdict requestApproval(RuntimeApprovalGate.ApprovalRequest request) {
        return this.local.requestApproval(request);
    }

    public boolean resolve(String approvalId, RuntimeApprovalGate.ApprovalVerdict verdict) {
        String origin = (String)this.approvalOrigin.get(approvalId);
        if (origin == null) {
            this.refreshRemotePending();
            origin = (String)this.approvalOrigin.get(approvalId);
        }
        if (origin != null) {
            return this.forwardResolve(origin, approvalId, verdict);
        }
        if (this.local.resolve(approvalId, verdict)) {
            return true;
        }
        for (RemoteTarget target : this.remoteTargets()) {
            if (!this.forwardResolve(target.baseUrl(), approvalId, verdict)) continue;
            return true;
        }
        return false;
    }

    public List<RuntimeApprovalGateway.PendingApprovalView> listPending() {
        ArrayList<RuntimeApprovalGateway.PendingApprovalView> merged = new ArrayList<RuntimeApprovalGateway.PendingApprovalView>(this.local.listPending());
        for (RuntimeApprovalGateway.PendingApprovalView item : this.remotePending()) {
            boolean duplicated = merged.stream().anyMatch(x -> x.approvalId().equals(item.approvalId()));
            if (duplicated) continue;
            merged.add(item);
        }
        return merged;
    }

    public int pendingCount() {
        return this.local.pendingCount();
    }

    private List<RemoteTarget> remoteTargets() {
        try {
            HashMap<String, String> nameByHuman = new HashMap<>();
            try {
                this.humanDao.listAll().forEach(h -> nameByHuman.put(h.id(), h.displayName()));
            }
            catch (Exception exception) {
                // empty catch block
            }
            LinkedHashMap<String, RemoteTarget> byBaseUrl = new LinkedHashMap<String, RemoteTarget>();
            for (DigitalHumanEndpointPO po : this.endpointDao.findAll()) {
                String type;
                String baseUrl = po.baseUrl();
                if (baseUrl == null || baseUrl.isBlank() || "local-dsh".equals(type = po.endpointType() == null ? "" : po.endpointType()) || "a2a".equals(type)) continue;
                String normalized = baseUrl.trim().replaceAll("/+$", "");
                byBaseUrl.computeIfAbsent(normalized, key -> new RemoteTarget((String)key, this.credentials.tokenFor(po.digitalHumanId()), nameByHuman.getOrDefault(po.digitalHumanId(), "")));
            }
            return List.copyOf(byBaseUrl.values());
        }
        catch (Exception exception) {
            log.warn("[approval] \u8fdc\u7aef\u7aef\u70b9\u679a\u4e3e\u5931\u8d25\uff0c\u672c\u8f6e\u4ec5\u805a\u5408\u672c\u5730\u5ba1\u6279\uff1a{}", (Object)exception.getMessage());
            return List.of();
        }
    }

    private List<RuntimeApprovalGateway.PendingApprovalView> remotePending() {
        if (System.currentTimeMillis() - this.remoteCacheAt < 1000L) {
            return this.remoteCache;
        }
        this.refreshRemotePending();
        return this.remoteCache;
    }

    private synchronized void refreshRemotePending() {
        if (System.currentTimeMillis() - this.remoteCacheAt < 1000L) {
            return;
        }
        ArrayList<RuntimeApprovalGateway.PendingApprovalView> merged = new ArrayList<RuntimeApprovalGateway.PendingApprovalView>();
        for (RemoteTarget target : this.remoteTargets()) {
            for (RuntimeApprovalGateway.PendingApprovalView item : this.fetchRemote(target)) {
                this.approvalOrigin.put(item.approvalId(), target.baseUrl());
                merged.add(item);
            }
        }
        if (this.approvalOrigin.size() > 512) {
            this.approvalOrigin.clear();
        }
        this.remoteCache = List.copyOf(merged);
        this.remoteCacheAt = System.currentTimeMillis();
    }

    private List<RuntimeApprovalGateway.PendingApprovalView> fetchRemote(RemoteTarget target) {
        try {
            HttpResponse<String> response;
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(target.baseUrl() + "/api/harness/approvals/runtime/pending")).timeout(REQUEST_TIMEOUT).GET();
            if (target.token() != null) {
                builder.header("Authorization", "Bearer " + target.token());
            }
            if ((response = this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())).statusCode() != 200) {
                return List.of();
            }
            JsonNode root = this.objectMapper.readTree(response.body());
            if (!SUCCESS_CODE.equals(root.path("code").asText())) {
                return List.of();
            }
            String label = target.displayName() == null || target.displayName().isBlank() ? "" : "\u300c" + target.displayName() + "\u300d";
            ArrayList<RuntimeApprovalGateway.PendingApprovalView> items = new ArrayList<RuntimeApprovalGateway.PendingApprovalView>();
            for (JsonNode node : root.path("data")) {
                items.add(new RuntimeApprovalGateway.PendingApprovalView(node.path("approvalId").asText(""), node.path("sessionId").asText(""), node.path("toolName").asText(""), label + node.path("displayCommand").asText(""), (Map)this.objectMapper.convertValue((Object)node.path("arguments"), (TypeReference)new TypeReference<Map<String, Object>>(){}), node.path("createdAt").asText("")));
            }
            return items;
        }
        catch (Exception exception) {
            log.debug("[approval] \u8fdc\u7aef\u5f85\u51b3\u5ba1\u6279\u83b7\u53d6\u5931\u8d25 base={}\uff1a{}", (Object)target.baseUrl(), (Object)exception.getMessage());
            return List.of();
        }
    }

    private boolean forwardResolve(String baseUrl, String approvalId, RuntimeApprovalGate.ApprovalVerdict verdict) {
        try {
            HttpResponse<String> response;
            String token = null;
            for (RemoteTarget target : this.remoteTargets()) {
                if (!target.baseUrl().equals(baseUrl)) continue;
                token = target.token();
                break;
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/harness/approvals/runtime/" + approvalId + "/resolve")).timeout(REQUEST_TIMEOUT).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(this.objectMapper.writeValueAsString(Map.of("verdict", verdict.name()))));
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            if ((response = this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())).statusCode() != 200) {
                return false;
            }
            JsonNode root = this.objectMapper.readTree(response.body());
            return SUCCESS_CODE.equals(root.path("code").asText()) && root.path("data").path("resolved").asBoolean(false);
        }
        catch (Exception exception) {
            log.warn("[approval] \u88c1\u51b3\u8f6c\u53d1\u5931\u8d25 base={} approval={}\uff1a{}", new Object[]{baseUrl, approvalId, exception.getMessage()});
            return false;
        }
    }

    private record RemoteTarget(String baseUrl, String token, String displayName) {
    }
}

