package net.linlan.dsh.plugin.emis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RS256 JWT 验签器：JWKS 本地验签 + kid 缓存；不做任何网络代理、不记录令牌。
 * 只接受 RS256（dsh-java 统一使用 OIDC access token），拒绝 HS256。
 */
public final class EmisTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(EmisTokenVerifier.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final EmisConfig config;
    private volatile Map<String, RSAPublicKey> keys = Map.of();
    private volatile long fetchedAt;

    public EmisTokenVerifier(EmisConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.timeoutMs()))
                .build();
    }

    public TokenClaims verify(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenException("EMIS_TOKEN_MISSING", "缺少 token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new TokenException("EMIS_TOKEN_INVALID", "token 格式非法");
        }
        JsonNode header = readJson(parts[0]);
        JsonNode payload = readJson(parts[1]);
        String alg = header.path("alg").asText("");
        if (!"RS256".equals(alg)) {
            throw new TokenException("EMIS_TOKEN_INVALID", "仅接受 RS256 OIDC token");
        }
        String kid = header.path("kid").asText("");
        if (kid.isBlank()) {
            throw new TokenException("EMIS_TOKEN_INVALID", "token 缺少 kid");
        }
        RSAPublicKey key = keyFor(kid);
        verifySignature(parts, key);

        String issuer = payload.path("iss").asText("");
        if (config.issuer() != null && !config.issuer().isBlank() && !config.issuer().equals(issuer)) {
            throw new TokenException("EMIS_TOKEN_INVALID", "issuer 不匹配");
        }
        Instant now = Instant.now();
        long exp = payload.path("exp").asLong(0);
        if (exp <= 0 || now.isAfter(Instant.ofEpochSecond(exp))) {
            throw new TokenException("EMIS_TOKEN_EXPIRED", "token 已过期");
        }
        long nbf = payload.path("nbf").asLong(0);
        if (nbf > 0 && now.plusSeconds(30).isBefore(Instant.ofEpochSecond(nbf))) {
            throw new TokenException("EMIS_TOKEN_INVALID", "token 尚未生效");
        }

        return new TokenClaims(
                payload.path("sub").asText(""),
                longOrNull(payload.get("tenant_id")),
                intOrNull(payload.get("role_level")),
                payload.path("user_name").asText(null),
                scopesOf(payload.get("scope")),
                issuer,
                Instant.ofEpochSecond(exp));
    }

    public Map<String, Object> describe(TokenClaims claims) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("subject", claims.subject());
        map.put("userId", claims.subject());
        map.put("tenantId", claims.tenantId());
        map.put("roleLevel", claims.roleLevel());
        map.put("userName", claims.userName());
        map.put("scopes", claims.scopes());
        map.put("issuer", claims.issuer());
        map.put("expiresAt", claims.expiresAt().toString());
        return map;
    }

    private RSAPublicKey keyFor(String kid) {
        long now = System.currentTimeMillis();
        if (keys.isEmpty() || now - fetchedAt > config.cacheMs()) {
            refreshKeys();
        }
        RSAPublicKey key = keys.get(kid);
        if (key == null) {
            refreshKeys();
            key = keys.get(kid);
        }
        if (key == null) {
            throw new TokenException("EMIS_TOKEN_INVALID", "kid 未在 JWKS 中找到");
        }
        return key;
    }

    private void refreshKeys() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + config.jwksPath()))
                    .timeout(Duration.ofMillis(config.timeoutMs()))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new TokenException("EMIS_JWKS_UNAVAILABLE", "JWKS 不可用（HTTP " + response.statusCode() + "）");
            }
            JsonNode root = mapper.readTree(response.body());
            Map<String, RSAPublicKey> refreshed = new LinkedHashMap<>();
            for (JsonNode jwk : root.path("keys")) {
                if (!"RSA".equals(jwk.path("kty").asText())) {
                    continue;
                }
                String id = jwk.path("kid").asText("");
                if (id.isBlank()) {
                    continue;
                }
                BigInteger modulus = new BigInteger(1, base64Url(jwk.path("n").asText()));
                BigInteger exponent = new BigInteger(1, base64Url(jwk.path("e").asText()));
                RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(modulus, exponent));
                refreshed.put(id, publicKey);
            }
            if (refreshed.isEmpty()) {
                throw new TokenException("EMIS_JWKS_UNAVAILABLE", "JWKS 为空");
            }
            this.keys = Map.copyOf(refreshed);
            this.fetchedAt = System.currentTimeMillis();
        } catch (TokenException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[dsh-emis-plugin] JWKS fetch failed: {}", ex.getClass().getSimpleName());
            throw new TokenException("EMIS_JWKS_UNAVAILABLE", "JWKS 拉取失败");
        }
    }

    private void verifySignature(String[] parts, RSAPublicKey key) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(base64Url(parts[2]))) {
                throw new TokenException("EMIS_TOKEN_INVALID", "签名校验失败");
            }
        } catch (TokenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TokenException("EMIS_TOKEN_INVALID", "签名校验失败");
        }
    }

    private JsonNode readJson(String base64Url) {
        try {
            return mapper.readTree(base64Url(base64Url));
        } catch (TokenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TokenException("EMIS_TOKEN_INVALID", "token 内容非法");
        }
    }

    private static byte[] base64Url(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (Exception ex) {
            throw new TokenException("EMIS_TOKEN_INVALID", "token 编码非法");
        }
    }

    private static List<String> scopesOf(JsonNode scope) {
        List<String> scopes = new ArrayList<>();
        if (scope == null || scope.isNull()) {
            return scopes;
        }
        if (scope.isArray()) {
            scope.forEach(item -> {
                if (!item.asText("").isBlank()) {
                    scopes.add(item.asText());
                }
            });
            return scopes;
        }
        for (String part : scope.asText("").trim().split("\\s+")) {
            if (!part.isBlank()) {
                scopes.add(part);
            }
        }
        return scopes;
    }

    private static Long longOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isNumber() ? node.longValue() : parseLong(node.asText(""));
    }

    private static Integer intOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.intValue();
        }
        try {
            return Integer.parseInt(node.asText(""));
        } catch (Exception ex) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
