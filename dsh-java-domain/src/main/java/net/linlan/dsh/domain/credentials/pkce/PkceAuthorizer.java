package net.linlan.dsh.domain.credentials.pkce;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

public class PkceAuthorizer {

    private static final int VERIFIER_LENGTH = 64;
    private static final String CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";

    private final PkceFlowPort flowPort;
    private final SecureRandom random;

    public PkceAuthorizer(PkceFlowPort flowPort) {
        if (flowPort == null) {
            throw new IllegalArgumentException("flow port required");
        }
        this.flowPort = flowPort;
        this.random = new SecureRandom();
    }

    public PkceAuthorizer(PkceFlowPort flowPort, SecureRandom random) {
        if (flowPort == null) {
            throw new IllegalArgumentException("flow port required");
        }
        this.flowPort = flowPort;
        this.random = Objects.requireNonNullElse(random, new SecureRandom());
    }

    public PkceResult authorize(String clientId, String redirectUri) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId required");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("redirectUri required");
        }
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String token = flowPort.exchange(clientId, redirectUri, codeVerifier, codeChallenge);
        return new PkceResult(token, codeVerifier, codeChallenge);
    }

    public String generateCodeVerifier() {
        StringBuilder sb = new StringBuilder(VERIFIER_LENGTH);
        for (int i = 0; i < VERIFIER_LENGTH; i++) {
            sb.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }

    public String generateCodeChallenge(String codeVerifier) {
        if (codeVerifier == null || codeVerifier.isBlank()) {
            throw new IllegalArgumentException("code verifier required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public interface PkceFlowPort {
        String exchange(String clientId, String redirectUri, String codeVerifier, String codeChallenge);
    }

    public record PkceResult(String accessToken, String codeVerifier, String codeChallenge) {}
}
