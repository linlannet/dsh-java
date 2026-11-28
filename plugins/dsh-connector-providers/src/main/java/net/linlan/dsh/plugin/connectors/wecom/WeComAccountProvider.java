package net.linlan.dsh.plugin.connectors.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.linlan.dsh.domain.connector.ConnectorAccountException;
import net.linlan.dsh.domain.connector.ConnectorConnection;
import net.linlan.dsh.domain.connector.ConnectorTokenGrant;
import net.linlan.dsh.domain.connector.IConnectorAccountPort;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 企业微信（WeCom）连接器账号提供方：以 corpid + corpsecret 换取 access_token。
 * <p>非交互式服务端取号，无需浏览器 OAuth；令牌仅进入服务端加密缓存，前端零接触。
 */
public final class WeComAccountProvider implements IConnectorAccountPort {

    public static final String CONNECTOR_ID = "wecom";
    public static final String DEFAULT_TOKEN_ENDPOINT = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";

    private final String corpId;
    private final String corpSecret;
    private final TokenClient tokenClient;
    private final Clock clock;

    public WeComAccountProvider(String corpId, String corpSecret, String tokenEndpoint) {
        this(corpId, corpSecret,
                new JdkHttpTokenClient(tokenEndpoint == null || tokenEndpoint.isBlank()
                        ? DEFAULT_TOKEN_ENDPOINT : tokenEndpoint),
                Clock.systemUTC());
    }

    /** 测试用：注入假 HTTP 客户端与固定时钟。 */
    WeComAccountProvider(String corpId, String corpSecret, TokenClient tokenClient, Clock clock) {
        this.corpId = corpId;
        this.corpSecret = corpSecret;
        this.tokenClient = tokenClient;
        this.clock = clock;
    }

    @Override
    public String connectorId() {
        return CONNECTOR_ID;
    }

    @Override
    public ConnectorConnection connect() {
        if (isBlank(corpId) || isBlank(corpSecret)) {
            throw new ConnectorAccountException("WECOM_CREDENTIALS_REQUIRED",
                    "企业微信 corpid/corpsecret 未配置，无法连接", false);
        }
        Token token = fetchToken();
        Instant expiresAt = token.expiresInSeconds() <= 0 ? null
                : clock.instant().plusSeconds(token.expiresInSeconds());
        return ConnectorConnection.authorized(new ConnectorTokenGrant(token.accessToken(), null, expiresAt));
    }

    /** 企业微信 access_token 无 refresh_token；到期用企业凭据重新换取。 */
    @Override
    public ConnectorConnection refresh(ConnectorTokenGrant current) {
        return connect();
    }

    private Token fetchToken() {
        try {
            return tokenClient.fetch(corpId, corpSecret);
        } catch (ConnectorAccountException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorAccountException("WECOM_TOKEN_FETCH_FAILED",
                    "获取企业微信 access_token 失败: " + safeMessage(e), true, e);
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
        Token fetch(String corpId, String corpSecret) throws Exception;
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
        public Token fetch(String corpId, String corpSecret) throws IOException, InterruptedException {
            String query = "corpid=" + urlEncode(corpId) + "&corpsecret=" + urlEncode(corpSecret);
            URI uri = URI.create(endpoint + (endpoint.contains("?") ? "&" : "?") + query);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ConnectorAccountException("WECOM_TOKEN_FETCH_FAILED",
                        "企业微信令牌接口返回 HTTP " + response.statusCode(), true);
            }
            JsonNode root = MAPPER.readTree(response.body());
            int errcode = root.path("errcode").asInt(-1);
            if (errcode != 0) {
                boolean retryable = errcode == -1;
                throw new ConnectorAccountException("WECOM_TOKEN_REJECTED",
                        "企业微信拒绝颁发令牌: errcode=" + errcode + " errmsg=" + root.path("errmsg").asText(""), retryable);
            }
            String token = root.path("access_token").asText(null);
            if (isBlank(token)) {
                throw new ConnectorAccountException("WECOM_TOKEN_REJECTED",
                        "企业微信令牌响应缺少 access_token", false);
            }
            return new Token(token, root.path("expires_in").asLong(0));
        }

        private static String urlEncode(String s) {
            return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
        }
    }
}
