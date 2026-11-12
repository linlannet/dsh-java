package net.linlan.dsh.cases.miniapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.linlan.dsh.api.IMiniAppApi;
import net.linlan.dsh.api.dto.MiniAppLoginRequestDTO;
import net.linlan.dsh.api.dto.MiniAppLoginResponseDTO;
import net.linlan.dsh.api.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 微信小程序登录用例。
 *
 * <p>使用 wx.login 的临时 code 调用微信 jscode2session 换取 openid，签发 2 小时短期会话令牌。
 * 未配置 appid/secret 时返回 50300，不降级为匿名登录。
 */
@Service
public class MiniAppLoginUseCase implements IMiniAppApi {

    private static final Logger log = LoggerFactory.getLogger(MiniAppLoginUseCase.class);
    private static final long SESSION_TTL_MS = 2 * 60 * 60 * 1000L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${dsh.miniapp.wechat.appid:}")
    private String appid;

    @Value("${dsh.miniapp.wechat.secret:}")
    private String secret;

    private final Map<String, Long> sessions = new ConcurrentHashMap<>();

    @Override
    public Response<MiniAppLoginResponseDTO> login(MiniAppLoginRequestDTO request) {
        if (isBlank(this.appid) || isBlank(this.secret)) {
            return Response.fail("50300", "小程序登录未配置（dsh.miniapp.wechat.appid / secret）");
        }
        if (request == null || isBlank(request.code())) {
            return Response.invalidArgument("code 不能为空");
        }

        try {
            String url = String.format(
                    "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                    encode(this.appid), encode(this.secret), encode(request.code()));
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(8))
                    .build();
            HttpResponse<String> httpResponse = HTTP.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode node = MAPPER.readTree(httpResponse.body());

            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                log.warn("小程序登录失败 errcode={} errmsg={}",
                        node.get("errcode").asInt(), node.path("errmsg").asText());
                return Response.fail("40101", "微信登录失败：" + node.path("errmsg").asText());
            }

            String openid = node.path("openid").asText();
            if (isBlank(openid)) {
                return Response.fail("40102", "微信未返回 openid");
            }

            this.purgeExpired();
            String token = "mini_" + RANDOM.nextInt(Integer.MAX_VALUE) + "_" + UUID.randomUUID();
            long expiresAt = System.currentTimeMillis() + SESSION_TTL_MS;
            this.sessions.put(token, expiresAt);
            return Response.success(new MiniAppLoginResponseDTO(token, Instant.ofEpochMilli(expiresAt).toString()));
        } catch (Exception error) {
            log.warn("小程序登录请求异常", error);
            return Response.fail("50200", "小程序登录请求失败");
        }
    }

    @Override
    public Response<Boolean> validateSession(String token) {
        if (isBlank(token)) {
            return Response.success(Boolean.FALSE);
        }
        Long expiresAt = this.sessions.get(token);
        if (expiresAt == null) {
            return Response.success(Boolean.FALSE);
        }
        if (System.currentTimeMillis() > expiresAt) {
            this.sessions.remove(token);
            return Response.success(Boolean.FALSE);
        }
        return Response.success(Boolean.TRUE);
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        this.sessions.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}