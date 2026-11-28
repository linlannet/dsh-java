package net.linlan.dsh.plugin.connectors.feishu;

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
 * 飞书（Lark）连接器账号提供方：以 app_id + app_secret 换取 tenant_access_token。
 * <p>非交互式 client_credentials 流程，无需浏览器 OAuth；令牌仅进入服务端加密缓存，前端零接触。
 */
public final class FeishuAccountProvider implements IConnectorAccountPort {

    public static final String CONNECTOR_ID = "feishu";
    public static final String DEFAULT_TOKEN_ENDPOINT =
            "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";

    private final String appId;
    private final String appSecret;
    private final TokenClient tokenClient;
    private final Clock clock;

    public FeishuAccountProvider(String appId, String appSecret, String tokenEndpoint) {
        this(appId, appSecret,
                new JdkHttpTokenClient(tokenEndpoint == null || tokenEndpoint.isBlank()
                        ? DEFAULT_TOKEN_ENDPOINT : tokenEndpoint),
                Clock.systemUTC());
    }

    /** 测试用：注入假 HTTP 客户端与固定时钟。 */
    FeishuAccountProvider(String appId, String appSecret, TokenClient tokenClient, Clock clock) {
        this.appId = appId;
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
        if (isBlank(appId) || isBlank(appSecret)) {
            throw new ConnectorAccountException("FEISHU_CREDENTIALS_REQUIRED",
                    "飞书 appId/appSecret 未配置，无法连接", false);
        }
        Token token = fetchToken();
        Instant expiresAt = token.expiresInSeconds() <= 0 ? null
                : clock.instant().plusSeconds(token.expiresInSeconds());
        return ConnectorConnection.authorized(new ConnectorTokenGrant(token.accessToken(), null, expiresAt));
    }

    /** 飞书 tenant_access_token 无 refresh_token；到期用应用凭据重新换取。 */
    @Override
    public ConnectorConnection refresh(ConnectorTokenGrant current) {
        return connect();
    }

    private Token fetchToken() {
        try {
            return tokenClient.fetch(appId, appSecret);
        } catch (ConnectorAccountException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorAccountException("FEISHU_TOKEN_FETCH_FAILED",
                    "获取飞书 tenant_access_token 失败: " + safeMessage(e), true, e);
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
        Token fetch(String appId, String appSecret) throws Exception;
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
        public Token fetch(String appId, String appSecret) throws IOException, InterruptedException {
            String body = "{\"app_id\":\"" + esc(appId) + "\",\"app_secret\":\"" + esc(appSecret) + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ConnectorAccountException("FEISHU_TOKEN_FETCH_FAILED",
                        "飞书令牌接口返回 HTTP " + response.statusCode(), true);
            }
            JsonNode root = MAPPER.readTree(response.body());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                // 厂商已明确拒绝（凭据/应用非法），重试不会改变结果，交由调用方修正配置。
                throw new ConnectorAccountException("FEISHU_TOKEN_REJECTED",
                        "飞书拒绝颁发令牌: code=" + code + " msg=" + root.path("msg").asText(""), false);
            }
            String token = root.path("tenant_access_token").asText(null);
            if (isBlank(token)) {
                throw new ConnectorAccountException("FEISHU_TOKEN_REJECTED",
                        "飞书令牌响应缺少 tenant_access_token", false);
            }
            return new Token(token, root.path("expire").asLong(0));
        }

        private static String esc(String s) {
            return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
