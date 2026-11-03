package net.linlan.dsh.infrastructure.adapter.feishu;

import net.linlan.dsh.domain.bot.adapter.port.IFeishuMessagingPort;
import net.linlan.dsh.domain.credentials.adapter.port.ICredentialProviderPort;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialRef;
import net.linlan.dsh.domain.credentials.model.valobj.ResolvedCredentialVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.CreateMessageRespBody;
import com.lark.oapi.service.im.v1.model.UpdateMessageReq;
import com.lark.oapi.service.im.v1.model.UpdateMessageReqBody;
import com.lark.oapi.service.im.v1.model.UpdateMessageResp;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 飞书出站消息适配器：按 appId 缓存 {@link Client}，发送文本/就地更新/Markdown 卡片。
 */
public class FeishuMessagingClient implements IFeishuMessagingPort {

    private static final Logger log = LoggerFactory.getLogger(FeishuMessagingClient.class);
    private static final int MAX_STREAM_TEXT = 20000;
    private static final int MAX_CARD_MARKDOWN = 28000;

    private final ICredentialProviderPort credentialPort;
    private final ObjectMapper objectMapper;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    /**
     * 构造出站消息适配器。
     *
     * @param credentialPort 凭据端口
     * @param objectMapper   JSON 序列化器
     */
    public FeishuMessagingClient(ICredentialProviderPort credentialPort, ObjectMapper objectMapper) {
        this.credentialPort = credentialPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public String sendText(String appId, String chatId, String text) {
        String safe = text == null ? "" : text;
        if (safe.length() > MAX_STREAM_TEXT) {
            safe = safe.substring(0, MAX_STREAM_TEXT);
        }
        return this.send(appId, chatId, "text", this.jsonText(safe));
    }

    @Override
    public boolean updateText(String appId, String messageId, String text) {
        try {
            UpdateMessageReq req = UpdateMessageReq.newBuilder()
                    .messageId(messageId)
                    .updateMessageReqBody(UpdateMessageReqBody.newBuilder()
                            .msgType("text")
                            .content(this.jsonText(text == null ? "" : text))
                            .build())
                    .build();
            UpdateMessageResp resp = this.client(appId).im().message().update(req);
            return resp.success();
        } catch (Exception e) {
            log.warn("[Feishu] 流式更新消息失败（可忽略，最终回复另发兜底）: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String sendMarkdownCard(String appId, String chatId, String markdown) {
        String md = markdown == null ? "" : markdown;
        if (md.length() > MAX_CARD_MARKDOWN) {
            md = md.substring(0, MAX_CARD_MARKDOWN) + "\n\n…（内容过长已截断）";
        }
        String content;
        try {
            content = this.objectMapper.writeValueAsString(Map.of(
                    "config", Map.of("wide_screen_mode", true),
                    "elements", new Object[]{Map.of("tag", "markdown", "content", md)}));
        } catch (Exception e) {
            throw new IllegalStateException("构造飞书卡片失败: " + e.getMessage(), e);
        }
        return this.send(appId, chatId, "interactive", content);
    }

    /**
     * 发送消息（内部通用）。
     *
     * @param appId   飞书应用 app_id
     * @param chatId  会话 ID
     * @param msgType 消息类型
     * @param content 内容 JSON
     * @return 消息 ID
     */
    private String send(String appId, String chatId, String msgType, String content) {
        try {
            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType("chat_id")
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(chatId)
                            .msgType(msgType)
                            .content(content)
                            .build())
                    .build();
            CreateMessageResp resp = this.client(appId).im().message().create(req);
            if (!resp.success()) {
                throw new IllegalStateException("飞书消息发送失败 code=" + resp.getCode() + " msg=" + resp.getMsg());
            }
            CreateMessageRespBody data = resp.getData();
            return data == null ? null : data.getMessageId();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("飞书消息发送异常: " + e.getMessage(), e);
        }
    }

    /**
     * 构造文本消息 JSON。
     *
     * @param text 文本
     * @return JSON 字符串
     */
    private String jsonText(String text) {
        try {
            return this.objectMapper.writeValueAsString(Map.of("text", text == null ? "" : text));
        } catch (Exception e) {
            throw new IllegalStateException("构造飞书文本失败: " + e.getMessage(), e);
        }
    }

    /**
     * 取已缓存的 SDK 客户端（包内可见，便于测试以桩客户端替换，不触碰真实网络）。
     *
     * @param appId 飞书应用 app_id
     * @return SDK 客户端
     */
    Client client(String appId) {
        Client cached = this.clients.get(appId);
        if (cached != null) {
            return cached;
        }
        return this.clients.computeIfAbsent(appId, id -> {
            throw new IllegalStateException("飞书应用 " + id + " 尚未注册（未启动长连接），无法发送消息");
        });
    }

    /**
     * 注册（预热）SDK 客户端。
     *
     * @param appId     飞书应用 app_id
     * @param secretRef 凭据引用键
     * @return 注册成功返回 true
     */
    public boolean register(String appId, String secretRef) {
        if (this.clients.containsKey(appId)) {
            return true;
        }
        Optional<String> secret = secretRef == null
                ? Optional.empty()
                : this.credentialPort.resolve(CredentialRef.of(secretRef)).map(ResolvedCredentialVO::value);
        if (secret.isEmpty() || secret.get().isBlank()) {
            log.warn("[Feishu] 凭据 {} 未解析到 app_secret，无法注册应用 {}", secretRef, appId);
            return false;
        }
        this.clients.put(appId, Client.newBuilder(appId, secret.get()).build());
        return true;
    }

    /**
     * 注销（清理）SDK 客户端。
     *
     * @param appId 飞书应用 app_id
     */
    public void unregister(String appId) {
        this.clients.remove(appId);
    }
}
