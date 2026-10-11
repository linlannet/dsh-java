package net.linlan.dsh.trigger.http;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentCardController {
    private static final String A2A_PROTOCOL_VERSION = "0.3.0";
    private final String displayName;
    private final String avatarUrl;
    private final String purpose;
    private final List<String> roleTags;
    private final List<String> tools;
    private final String approvalPolicy;
    private final int maxConcurrentTasks;
    private final String configuredApiKeys;

    public AgentCardController(@Value(value="${harness.agent-card.display-name:\u672c\u5730\u52a9\u624b}") String displayName, @Value(value="${harness.agent-card.avatar-url:}") String avatarUrl, @Value(value="${harness.agent-card.purpose:\u901a\u7528\u667a\u80fd\u4f53\u670d\u52a1\uff0c\u53ef\u627f\u62c5\u5f00\u53d1\u3001\u5206\u6790\u4e0e\u6587\u6863\u4efb\u52a1}") String purpose, @Value(value="${harness.agent-card.role-tags:general}") List<String> roleTags, @Value(value="${harness.agent-card.tools:fs.read,web.search}") List<String> tools, @Value(value="${harness.agent-card.approval-policy:WRITE_REQUIRES_APPROVAL}") String approvalPolicy, @Value(value="${harness.agent-card.max-concurrent-tasks:1}") int maxConcurrentTasks, @Value(value="${harness.auth.api-keys:}") String configuredApiKeys) {
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.purpose = purpose;
        this.roleTags = roleTags;
        this.tools = tools;
        this.approvalPolicy = approvalPolicy;
        this.maxConcurrentTasks = maxConcurrentTasks;
        this.configuredApiKeys = configuredApiKeys;
    }

    @GetMapping(value={"/.well-known/dsh-agent-card"})
    public Map<String, Object> agentCard() {
        LinkedHashMap<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("schemaVersion", "1.0");
        card.put("digitalHumanId", "local");
        card.put("displayName", this.displayName);
        if (this.avatarUrl != null && !this.avatarUrl.isBlank()) {
            card.put("avatarUrl", this.avatarUrl);
        }
        card.put("purpose", this.purpose);
        card.put("roleTags", this.roleTags);
        card.put("capabilities", List.of(Map.of("name", "agent-task", "description", this.purpose, "input", "objective", "output", "report")));
        card.put("tools", this.tools);
        card.put("approvalPolicy", this.approvalPolicy);
        card.put("maxConcurrentTasks", this.maxConcurrentTasks);
        card.put("protocol", "dsh.v1");
        card.put("endpoints", Map.of("stream", "/api/agent/stream", "approvals", "/api/harness/approvals"));
        return card;
    }

    @GetMapping(value={"/.well-known/agent-card.json"})
    public Map<String, Object> a2aAgentCard(HttpServletRequest request) {
        return this.buildA2aCard(request);
    }

    @GetMapping(value={"/.well-known/agent.json"})
    public Map<String, Object> a2aAgentCardLegacy(HttpServletRequest request) {
        return this.buildA2aCard(request);
    }

    private Map<String, Object> buildA2aCard(HttpServletRequest request) {
        boolean hasApiKeys;
        LinkedHashMap<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("name", this.displayName);
        card.put("description", this.purpose);
        card.put("url", this.serviceBaseUrl(request) + "/a2a");
        card.put("preferredTransport", "JSONRPC");
        card.put("protocolVersion", A2A_PROTOCOL_VERSION);
        card.put("version", "0.1.7");
        if (this.avatarUrl != null && !this.avatarUrl.isBlank()) {
            card.put("iconUrl", this.avatarUrl);
        }
        card.put("provider", Map.of("organization", "linlannet", "url", "https://github.com/linlannet/dsh-java"));
        card.put("capabilities", Map.of("streaming", true, "pushNotifications", false, "stateTransitionHistory", false));
        card.put("defaultInputModes", List.of("text/plain"));
        card.put("defaultOutputModes", List.of("text/plain"));
        card.put("skills", List.of(this.buildAgentTaskSkill()));
        boolean bl = hasApiKeys = this.configuredApiKeys != null && !this.configuredApiKeys.isBlank();
        if (hasApiKeys) {
            card.put("securitySchemes", Map.of("bearer", Map.of("type", "http", "scheme", "bearer")));
            card.put("security", List.of(Map.of("bearer", List.of())));
        }
        return card;
    }

    private Map<String, Object> buildAgentTaskSkill() {
        LinkedHashMap<String, Object> skill = new LinkedHashMap<String, Object>();
        skill.put("id", "agent-task");
        skill.put("name", this.displayName);
        skill.put("description", this.purpose);
        ArrayList<String> tags = new ArrayList<String>(this.roleTags);
        if (tags.isEmpty()) {
            tags.add("general");
        }
        skill.put("tags", tags);
        return skill;
    }

    private String serviceBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme() == null ? "http" : request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = "http".equals(scheme) && port == 80 || "https".equals(scheme) && port == 443;
        StringBuilder builder = new StringBuilder(scheme).append("://").append(host);
        if (!defaultPort && port > 0) {
            builder.append(':').append(port);
        }
        return builder.toString();
    }
}

