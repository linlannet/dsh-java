package net.linlan.dsh.plugin.connectors.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.linlan.dsh.domain.connector.ConnectorAccountException;
import net.linlan.dsh.domain.connector.ConnectorConnection;
import net.linlan.dsh.domain.connector.ConnectorTokenGrant;
import net.linlan.dsh.domain.connector.IConnectorAccountPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 钉钉（DingTalk）连接器账号提供方：以 appKey + appSecret 换取 accessToken。
 * <p>走新版服务端取号接口 {@code /v1.0/oauth2/accessToken}，无需浏览器 OAuth；令牌仅进入服务端加密缓存，前端零接触。
 */
public final class DingTalkAccountProvider implements IConnectorAccountPort {

    public static final String CONNECTOR_ID = "dingtalk";
    public static final String DEFAULT_TOKEN_ENDPOINT = "https://api.dingtalk.com/v1.0/oauth2/accessToken";

    private final String appKey;
    private final String appSecret;
    private final TokenClient tokenClient;
    private final Clock clock;

    public DingTalkAccountProvider(String appKey, String appSecret, String tokenEndpoint) {
        this(appKey, appSecret,
                new JdkHttpTokenClient(tokenEndpoint == null || tokenEndpoint.isBlank()
                        ? DEFAULT_TOKEN_ENDPOINT : tokenEndpoint),
                Clock.systemUTC());
    }

    /** 测试用：注入假 HTTP 客户端与固定时钟。 */
    DingTalkAccountProvider(String appKey, String appSecret, TokenClient tokenClient, Clock clock) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.tokenClient = tokenClient;
        this.clock = clock;
    }

    @Override
    public String connectorId() {
        return CONNECTOR_ID;
    }

    @Override
    public ConnectorConnection connect() {
        if (isBlank(appKey) || isBlank(appSecret)) {
            throw new ConnectorAccountException("DINGTALK_CREDENTIALS_REQUIRED",
                    "钉钉 appKey/appSecret 未配置，无法连接", false);
        }
        Token token = fetchToken();
        Instant expiresAt = token.expiresInSeconds() <= 0 ? null
                : clock.instant().plusSeconds(token.expiresInSeconds());
        return ConnectorConnection.authorized(new ConnectorTokenGrant(token.accessToken(), null, expiresAt));
    }

    /** 钉钉 accessToken 无 refresh_token；到期用应用凭据重新换取。 */
    @Override
    public ConnectorConnection refresh(ConnectorTokenGrant current) {
        return connect();
    }

    private Token fetchToken() {
        try {
            return tokenClient.fetch(appKey, appSecret);
        } catch (ConnectorAccountException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorAccountException("DINGTALK_TOKEN_FETCH_FAILED",
                    "获取钉钉 accessToken 失败: " + safeMessage(e), true, e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safeMessage(Throwable e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }

    record Token(String accessToken, long expiresInSeconds) {
    }

    /** 可替换的令牌获取，便于测试注入假实现。 */
    @FunctionalInterface
    interface TokenClient {
        Token fetch(String appKey, String appSecret) throws Exception;
    }

    /** 基于 JDK HttpClient 的真实实现；不引入第三方 HTTP 依赖。 */
    static final class JdkHttpTokenClient implements TokenClient {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private final String endpoint;
        private final HttpClient httpClient;

        JdkHttpTokenClient(String endpoint) {
            this(endpoint, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        }

        JdkHttpTokenClient(String endpoint, HttpClient httpClient) {
            this.endpoint = endpoint;
            this.httpClient = httpClient;
        }

        @Override
        public Token fetch(String appKey, String appSecret) throws IOException, InterruptedException {
            String body = "{\"appKey\":\"" + esc(appKey) + "\",\"appSecret\":\"" + esc(appSecret) + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw statusFailure(response.statusCode(), response.body());
            }
            JsonNode root = MAPPER.readTree(response.body());
            String token = root.path("accessToken").asText(null);
            if (isBlank(token)) {
                throw new ConnectorAccountException("DINGTALK_TOKEN_REJECTED",
                        "钉钉令牌响应缺少 accessToken: " + root.path("message").asText(""), false);
            }
            return new Token(token, root.path("expireIn").asLong(0));
        }

        /**
         * 非 2xx 分支：钉钉对非法凭据返回 HTTP 4xx 且正文带 {@code code}/{@code message}，
         * 需按"厂商拒绝"处理并保留原始错误信息；仅 5xx 才视为可重试的取号失败。
         */
        private static ConnectorAccountException statusFailure(int statusCode, String body) {
            JsonNode detail = parseQuietly(body);
            String code = detail == null ? null : detail.path("code").asText(null);
            if (code != null && !code.isBlank()) {
                return new ConnectorAccountException("DINGTALK_TOKEN_REJECTED",
                        "钉钉拒绝颁发令牌: HTTP " + statusCode + " code=" + code
                                + " message=" + detail.path("message").asText(""), retryableStatus(statusCode));
            }
            return new ConnectorAccountException("DINGTALK_TOKEN_FETCH_FAILED",
                    "钉钉令牌接口返回 HTTP " + statusCode, retryableStatus(statusCode));
        }

        private static boolean retryableStatus(int statusCode) {
            return statusCode >= 500 || statusCode == 429;
        }

        private static JsonNode parseQuietly(String body) {
            if (isBlank(body)) {
                return null;
            }
            try {
                return MAPPER.readTree(body);
            } catch (Exception ignored) {
                return null;
            }
        }

        private static String esc(String s) {
            return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
