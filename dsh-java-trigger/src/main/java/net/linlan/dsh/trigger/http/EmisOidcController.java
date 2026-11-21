package net.linlan.dsh.trigger.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.linlan.dsh.api.dto.EmisIdentityDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.trigger.filter.RouteAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
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
 * 企业身份登录（linlan-emis OIDC 授权码 + PKCE）。
 *
 * <p>RS256 token 只保存在宿主 HttpSession，前端不接触 token；回调后前端通过 /me 读取身份。</p>
 * <p>未配置（client-id/secret 缺失）时 login 返回 50300，不降级为匿名登录。</p>
 */
@RestController
@RequestMapping("/api/emis")
public class EmisOidcController {

    private static final Logger log = LoggerFactory.getLogger(EmisOidcController.class);
    private static final String SESSION_STATE = "emis_oidc_state";
    private static final String SESSION_VERIFIER = "emis_oidc_verifier";
    private static final String SESSION_RETURN = "emis_oidc_return";
    private static final String SESSION_IDENTITY = "emis_identity";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmisOidcProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private volatile Map<String, RSAPublicKey> keys = Map.of();
    private volatile long keysFetchedAt;

    public EmisOidcController(EmisOidcProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getTimeoutMs()))
                .build();
    }

    @GetMapping("/login")
    @RouteAuth(RouteAuth.Level.ANONYMOUS)
    public void login(@RequestParam(name = "return", required = false, defaultValue = "/") String returnTo,
                      HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!props.isConfigured()) {
            writeError(response, 503, "50300", "企业身份未配置：请设置 DSH_EMIS_OIDC_CLIENT_ID / CLIENT_SECRET");
            return;
        }
        HttpSession session = request.getSession(true);
        String state = randomToken();
        String verifier = randomToken();
        session.setAttribute(SESSION_STATE, state);
        session.setAttribute(SESSION_VERIFIER, verifier);
        session.setAttribute(SESSION_RETURN, safeReturn(returnTo));

        String authorizeUrl = props.getBaseUrl() + "/oauth2/authorize"
                + "?response_type=code"
                + "&client_id=" + enc(props.getClientId())
                + "&redirect_uri=" + enc(props.getRedirectUri())
                + "&scope=" + enc(props.getScopes())
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(challenge(verifier))
                + "&code_challenge_method=S256";
        response.sendRedirect(authorizeUrl);
    }

    @GetMapping("/callback")
    @RouteAuth(RouteAuth.Level.ANONYMOUS)
    public void callback(@RequestParam(name = "code", required = false) String code,
                         @RequestParam(name = "state", required = false) String state,
                         @RequestParam(name = "error", required = false) String error,
                         HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            writeError(response, 400, "40111", "登录会话已失效，请重新登录");
            return;
        }
        String expectedState = (String) session.getAttribute(SESSION_STATE);
        String verifier = (String) session.getAttribute(SESSION_VERIFIER);
        String returnTo = (String) session.getAttribute(SESSION_RETURN);
        session.removeAttribute(SESSION_STATE);
        session.removeAttribute(SESSION_VERIFIER);
        session.removeAttribute(SESSION_RETURN);

        if (expectedState == null || state == null || !MessageDigest.isEqual(
                expectedState.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            writeError(response, 400, "40111", "state 校验失败，请重新登录");
            return;
        }
        if (error != null && !error.isBlank()) {
            writeError(response, 401, "40110", "已取消授权");
            return;
        }
        if (code == null || code.isBlank()) {
            writeError(response, 400, "40112", "缺少授权码");
            return;
        }
        try {
            JsonNode token = exchange(code, verifier);
            String idToken = token.path("id_token").asText("");
            String accessToken = token.path("access_token").asText("");
            String raw = idToken.isBlank() ? accessToken : idToken;
            if (raw.isBlank()) {
                throw new OidcException("50200", "token 响应缺少 id_token/access_token");
            }
            JsonNode claims = verify(raw);
            EmisIdentityDTO identity = toIdentity(claims);
            session.setAttribute(SESSION_IDENTITY, identity);
            session.setAttribute("emis_access_token", accessToken);
            session.setAttribute("emis_id_token", idToken);
            response.sendRedirect(safeReturn(returnTo));
        } catch (OidcException ex) {
            log.warn("[emis-oidc] callback rejected: {}", ex.code);
            writeError(response, 502, ex.code, ex.getMessage());
        } catch (Exception ex) {
            log.warn("[emis-oidc] callback failed: {}", ex.getClass().getSimpleName());
            writeError(response, 502, "50200", "企业身份服务暂时不可用");
        }
    }

    @GetMapping("/me")
    @RouteAuth(RouteAuth.Level.ANONYMOUS)
    public Response<EmisIdentityDTO> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object identity = session == null ? null : session.getAttribute(SESSION_IDENTITY);
        if (identity instanceof EmisIdentityDTO dto) {
            return Response.success(dto);
        }
        return Response.fail("40100", "未登录");
    }

    @PostMapping("/logout")
    @RouteAuth(RouteAuth.Level.ANONYMOUS)
    public Response<Map<String, String>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        Map<String, String> data = new LinkedHashMap<>();
        if (props.isEnabled()) {
            data.put("logoutUrl", props.getBaseUrl() + "/connect/logout"
                    + "?post_logout_redirect_uri=" + enc(props.getPostLogoutRedirectUri())
                    + "&client_id=" + enc(props.getClientId()));
        }
        return Response.success(data);
    }

    private JsonNode exchange(String code, String verifier) {
        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(props.getRedirectUri())
                + "&code_verifier=" + enc(verifier)
                + "&client_id=" + enc(props.getClientId());
        String basic = Base64.getEncoder().encodeToString(
                (props.getClientId() + ":" + props.getClientSecret()).getBytes(StandardCharsets.UTF_8));
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getBaseUrl() + "/oauth2/token"))
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .header("Authorization", "Basic " + basic)
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new OidcException("40114", "token 交换失败（HTTP " + response.statusCode() + "）");
            }
            return mapper.readTree(response.body());
        } catch (OidcException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OidcException("50200", "无法连接企业身份服务");
        }
    }

    private JsonNode verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new OidcException("40112", "token 格式非法");
        }
        try {
            JsonNode header = mapper.readTree(base64Url(parts[0]));
            JsonNode payload = mapper.readTree(base64Url(parts[1]));
            if (!"RS256".equals(header.path("alg").asText(""))) {
                throw new OidcException("40112", "仅接受 RS256 token");
            }
            String kid = header.path("kid").asText("");
            RSAPublicKey key = keyFor(kid);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(base64Url(parts[2]))) {
                throw new OidcException("40112", "token 验签失败");
            }
            if (!props.getIssuer().isBlank()
                    && !props.getIssuer().equals(payload.path("iss").asText(""))) {
                throw new OidcException("40112", "issuer 不匹配");
            }
            long exp = payload.path("exp").asLong(0);
            if (exp <= 0 || Instant.now().isAfter(Instant.ofEpochSecond(exp))) {
                throw new OidcException("40112", "token 已过期");
            }
            return payload;
        } catch (OidcException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OidcException("40112", "token 解析失败");
        }
    }

    private RSAPublicKey keyFor(String kid) {
        long now = System.currentTimeMillis();
        if (keys.isEmpty() || now - keysFetchedAt > 600_000L) {
            refreshKeys();
        }
        RSAPublicKey key = keys.get(kid);
        if (key == null) {
            refreshKeys();
            key = keys.get(kid);
        }
        if (key == null) {
            throw new OidcException("40112", "kid 未在 JWKS 中找到");
        }
        return key;
    }

    private void refreshKeys() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getBaseUrl() + "/oauth2/jwks"))
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new OidcException("50200", "JWKS 不可用");
            }
            Map<String, RSAPublicKey> refreshed = new LinkedHashMap<>();
            for (JsonNode jwk : mapper.readTree(response.body()).path("keys")) {
                if (!"RSA".equals(jwk.path("kty").asText())) {
                    continue;
                }
                String kid = jwk.path("kid").asText("");
                if (kid.isBlank()) {
                    continue;
                }
                BigInteger modulus = new BigInteger(1, base64Url(jwk.path("n").asText()));
                BigInteger exponent = new BigInteger(1, base64Url(jwk.path("e").asText()));
                RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(modulus, exponent));
                refreshed.put(kid, publicKey);
            }
            if (refreshed.isEmpty()) {
                throw new OidcException("50200", "JWKS 为空");
            }
            this.keys = Map.copyOf(refreshed);
            this.keysFetchedAt = System.currentTimeMillis();
        } catch (OidcException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OidcException("50200", "JWKS 拉取失败");
        }
    }

    private EmisIdentityDTO toIdentity(JsonNode claims) {
        Long userId = parseLong(claims.path("sub").asText(""));
        return new EmisIdentityDTO(
                userId,
                parseLong(claims.path("tenant_id").asText("")),
                parseInt(claims.path("role_level").asText("")),
                claims.path("user_name").asText(""),
                scopesOf(claims.path("scope")),
                claims.path("iss").asText(""),
                claims.path("exp").asLong(0));
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

    private static void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(new ObjectMapper().writeValueAsString(Response.fail(code, message)));
    }

    private static String safeReturn(String value) {
        if (value == null || value.isBlank()) {
            return "/";
        }
        if (value.startsWith("/") && !value.startsWith("//")) {
            return value;
        }
        return "/";
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] base64Url(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (Exception ex) {
            throw new OidcException("40112", "token 编码非法");
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static final class OidcException extends RuntimeException {
        private final String code;

        private OidcException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}