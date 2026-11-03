package net.linlan.dsh.infrastructure.adapter.feishu;

import net.linlan.dsh.domain.bot.adapter.port.IFeishuPairingPort;
import net.linlan.dsh.domain.bot.model.valobj.FeishuPairingBeginVO;
import net.linlan.dsh.domain.bot.model.valobj.FeishuPairingPollVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 飞书一键配对适配器：基于 JDK {@link HttpClient} 调用设备授权 OAuth 端点。
 */
public class FeishuPairingClient implements IFeishuPairingPort {

    private static final Logger log = LoggerFactory.getLogger(FeishuPairingClient.class);
    private static final String ENDPOINT = "/oauth/v1/app/registration";
    private static final String FEISHU_BASE = "https://accounts.feishu.cn";
    private static final String LARK_BASE = "https://accounts.larksuite.com";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
    private final ObjectMapper objectMapper;

    /**
     * 构造配对适配器。
     *
     * @param objectMapper JSON 解析器
     */
    public FeishuPairingClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public FeishuPairingBeginVO begin(String appName, String appDesc) {
        return this.begin(appName, appDesc, FEISHU_BASE);
    }

    /**
     * 发起设备授权（可指定账号域，供 Lark 与测试使用）。
     *
     * @param appName 应用名
     * @param appDesc 应用描述
     * @param baseUrl 账号域基址
     * @return begin 结果
     */
    FeishuPairingBeginVO begin(String appName, String appDesc, String baseUrl) {
        String form = "action=begin&archetype=PersonalAgent&auth_method=client_secret&request_user_info=open_id";
        JsonNode body;
        try {
            body = this.post(baseUrl, form);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("发起飞书设备授权失败: " + e.getMessage(), e);
        }
        String rawUrl = body.path("verification_uri_complete").asText(null);
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalStateException("飞书设备授权 begin 响应缺少 verification_uri_complete: " + body);
        }
        StringBuilder url = new StringBuilder(rawUrl);
        url.append(rawUrl.contains("?") ? '&' : '?').append("source=").append(this.enc("dsh-java"));
        if (appName != null && !appName.isBlank()) {
            url.append("&name=").append(this.enc(appName));
        }
        if (appDesc != null && !appDesc.isBlank()) {
            url.append("&desc=").append(this.enc(appDesc));
        }
        return new FeishuPairingBeginVO(url.toString(), body.path("user_code").asText(null),
                body.path("device_code").asText(null), body.path("expires_in").asLong(3600L),
                body.path("interval").asInt(5));
    }

    @Override
    public FeishuPairingPollVO poll(String baseUrl, String deviceCode) {
        try {
            return this.doPoll(baseUrl, deviceCode);
        } catch (IOException | InterruptedException e) {
            log.warn("[Feishu] 设备授权轮询网络异常（按 pending 处理）: {}", e.getMessage());
            return new FeishuPairingPollVO(null, null, null, null, "authorization_pending", e.getMessage());
        }
    }

    /**
     * 执行一次轮询请求。
     */
    private FeishuPairingPollVO doPoll(String baseUrl, String deviceCode) throws IOException, InterruptedException {
        String form = "action=poll&device_code=" + this.enc(deviceCode);
        JsonNode body = this.post(baseUrl, form);
        JsonNode userInfo = body.path("user_info");
        return new FeishuPairingPollVO(body.path("client_id").asText(null), body.path("client_secret").asText(null),
                userInfo.path("open_id").asText(null), userInfo.path("tenant_brand").asText(null),
                body.hasNonNull("error") ? body.get("error").asText() : null,
                body.path("error_description").asText(null));
    }

    @Override
    public String larkBaseUrl() {
        return LARK_BASE;
    }

    /**
     * 表单 POST 并解析 JSON。
     */
    private JsonNode post(String baseUrl, String form) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + ENDPOINT))
                .timeout(Duration.ofSeconds(15L))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = this.http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 500) {
            throw new IOException("飞书设备授权服务异常 HTTP " + resp.statusCode() + ": " + resp.body());
        }
        try {
            return this.objectMapper.readTree(resp.body());
        } catch (Exception e) {
            throw new IOException("飞书设备授权响应解析失败 HTTP " + resp.statusCode() + ": " + resp.body(), e);
        }
    }

    /**
     * URL 编码。
     */
    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
