package net.linlan.dsh.infrastructure.digitalhuman;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class RemoteEndpointCredentialRegistry {
    private final ConcurrentMap<String, String> tokenByHumanId = new ConcurrentHashMap<String, String>();

    public void remember(String digitalHumanId, String token) {
        if (digitalHumanId == null || digitalHumanId.isBlank()) {
            return;
        }
        if (token == null || token.isBlank()) {
            return;
        }
        this.tokenByHumanId.put(digitalHumanId, token);
    }

    public String tokenFor(String digitalHumanId) {
        return digitalHumanId == null ? null : (String)this.tokenByHumanId.get(digitalHumanId);
    }
}

