package net.linlan.dsh.domain.webhook.github;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GitHubWebhookAdapter Webhook 适配")
class GitHubWebhookAdapterTest {
    @Test
    @DisplayName("签名验证失败返回 false")
    void verifySignatureFail() {
        var adapter = new GitHubWebhookAdapter();
        assertFalse(adapter.verifySignature("secret", "body", "sha256=invalid"));
    }

    @Test
    @DisplayName("null 签名头返回 false")
    void nullSignature() {
        var adapter = new GitHubWebhookAdapter();
        assertFalse(adapter.verifySignature("secret", "body", null));
    }

    @Test
    @DisplayName("projectDelivery 剥离 GitHub 专有字段")
    void projectDelivery() {
        var adapter = new GitHubWebhookAdapter();
        var delivery = adapter.projectDelivery(Map.of("action", "opened", "repository", Map.of("name", "repo")));
        assertEquals("opened", delivery.get("event"));
    }
}
