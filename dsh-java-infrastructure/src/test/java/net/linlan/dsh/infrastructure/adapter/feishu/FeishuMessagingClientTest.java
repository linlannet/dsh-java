package net.linlan.dsh.infrastructure.adapter.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.CreateMessageRespBody;
import com.lark.oapi.service.im.v1.model.UpdateMessageReq;
import com.lark.oapi.service.im.v1.model.UpdateMessageResp;
import com.lark.oapi.service.im.v1.resource.Message;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.linlan.dsh.domain.credentials.adapter.port.ICredentialProviderPort;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialInfoVO;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialRef;
import net.linlan.dsh.domain.credentials.model.valobj.ResolvedCredentialVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 出站消息适配器测试：mock 飞书 SDK，校验发送/就地编辑/卡片与端口层截断阈值，全程不访问真实网络。
 */
class FeishuMessagingClientTest {

    private static final String APP_ID = "cli_test";
    private static final String SECRET_REF = "FEISHU_BOT_FBOT_TEST_APP_SECRET";
    private static final int MAX_TEXT = 20000;
    private static final int MAX_CARD = 28000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FakeCredentialPort credentials;
    private FeishuMessagingClient plain;
    private Message messageApi;
    private StubMessagingClient stubbed;

    @BeforeEach
    void setUp() {
        this.credentials = new FakeCredentialPort();
        this.plain = new FeishuMessagingClient(this.credentials, this.objectMapper);
        Client sdk = mock(Client.class);
        ImService im = mock(ImService.class);
        this.messageApi = mock(Message.class);
        when(sdk.im()).thenReturn(im);
        when(im.message()).thenReturn(this.messageApi);
        this.stubbed = new StubMessagingClient(this.credentials, sdk);
    }

    @Test
    void registerFailsWhenSecretMissingAndSendIsRejected() {
        assertFalse(this.plain.register(APP_ID, SECRET_REF), "无 app_secret 时注册应失败");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> this.plain.sendText(APP_ID, "oc_1", "hi"));
        assertTrue(error.getMessage().contains("尚未注册"), error.getMessage());
    }

    @Test
    void registerSucceedsAndIsIdempotent() {
        this.credentials.put(SECRET_REF, "secret-value");
        assertTrue(this.plain.register(APP_ID, SECRET_REF));
        assertTrue(this.plain.register(APP_ID, SECRET_REF), "重复注册应保持成功");
    }

    @Test
    void unregisterRemovesClientAndBlocksSend() {
        this.credentials.put(SECRET_REF, "secret-value");
        assertTrue(this.plain.register(APP_ID, SECRET_REF));

        this.plain.unregister(APP_ID);

        assertThrows(IllegalStateException.class, () -> this.plain.sendText(APP_ID, "oc_1", "hi"));
    }

    @Test
    void sendTextReturnsMessageIdAndTruncatesAtLimit() throws Exception {
        this.credentials.put(SECRET_REF, "secret-value");
        this.stubbed.register(APP_ID, SECRET_REF);
        CreateMessageResp resp = this.createResp("om_1");
        when(this.messageApi.create(any(CreateMessageReq.class))).thenReturn(resp);

        String messageId = this.stubbed.sendText(APP_ID, "oc_1", "x".repeat(MAX_TEXT + 500));

        assertEquals("om_1", messageId);
        CreateMessageReq req = this.capturedCreate();
        assertEquals("text", req.getCreateMessageReqBody().getMsgType());
        assertEquals("chat_id", req.getReceiveIdType());
        assertEquals("oc_1", req.getCreateMessageReqBody().getReceiveId());
        JsonNode content = this.objectMapper.readTree(req.getCreateMessageReqBody().getContent());
        assertEquals(MAX_TEXT, content.path("text").asText().length(), "端口层应兜底截断到 20000");
    }

    @Test
    void sendMarkdownCardUsesInteractiveTypeAndTruncates() throws Exception {
        this.credentials.put(SECRET_REF, "secret-value");
        this.stubbed.register(APP_ID, SECRET_REF);
        CreateMessageResp resp = this.createResp("om_card");
        when(this.messageApi.create(any(CreateMessageReq.class))).thenReturn(resp);

        String messageId = this.stubbed.sendMarkdownCard(APP_ID, "oc_1", "#".repeat(MAX_CARD + 800));

        assertEquals("om_card", messageId);
        CreateMessageReq req = this.capturedCreate();
        assertEquals("interactive", req.getCreateMessageReqBody().getMsgType());
        JsonNode content = this.objectMapper.readTree(req.getCreateMessageReqBody().getContent());
        assertTrue(content.path("config").path("wide_screen_mode").asBoolean(), "卡片应为宽屏模式");
        String markdown = content.path("elements").get(0).path("content").asText();
        assertTrue(markdown.endsWith("…（内容过长已截断）"), "超长卡片应追加截断提示");
        assertTrue(markdown.length() <= MAX_CARD + "…（内容过长已截断）".length() + 2);
    }

    @Test
    void updateTextReturnsSuccessFlag() throws Exception {
        this.credentials.put(SECRET_REF, "secret-value");
        this.stubbed.register(APP_ID, SECRET_REF);
        UpdateMessageResp ok = mock(UpdateMessageResp.class);
        when(ok.success()).thenReturn(true);
        when(this.messageApi.update(any(UpdateMessageReq.class))).thenReturn(ok);

        assertTrue(this.stubbed.updateText(APP_ID, "om_1", "流式增量"));

        UpdateMessageResp failed = mock(UpdateMessageResp.class);
        when(failed.success()).thenReturn(false);
        when(this.messageApi.update(any(UpdateMessageReq.class))).thenReturn(failed);
        assertFalse(this.stubbed.updateText(APP_ID, "om_1", "again"), "失败应返回 false 而非抛出");
    }

    @Test
    void updateTextSwallowsExceptionAsFalse() throws Exception {
        this.credentials.put(SECRET_REF, "secret-value");
        this.stubbed.register(APP_ID, SECRET_REF);
        when(this.messageApi.update(any(UpdateMessageReq.class))).thenThrow(new RuntimeException("限流"));

        assertFalse(this.stubbed.updateText(APP_ID, "om_1", "hi"), "就地编辑异常应降级为 false");
    }

    private CreateMessageResp createResp(String messageId) {
        CreateMessageResp resp = mock(CreateMessageResp.class);
        CreateMessageRespBody body = mock(CreateMessageRespBody.class);
        when(body.getMessageId()).thenReturn(messageId);
        when(resp.success()).thenReturn(true);
        when(resp.getData()).thenReturn(body);
        return resp;
    }

    private CreateMessageReq capturedCreate() throws Exception {
        ArgumentCaptor<CreateMessageReq> captor = ArgumentCaptor.forClass(CreateMessageReq.class);
        verify(this.messageApi).create(captor.capture());
        return captor.getValue();
    }

    /**
     * 以桩 SDK 客户端替换真实缓存，避免触碰网络。
     */
    private static final class StubMessagingClient extends FeishuMessagingClient {

        private final Client stub;

        private StubMessagingClient(ICredentialProviderPort credentialPort, Client stub) {
            super(credentialPort, new ObjectMapper());
            this.stub = stub;
        }

        @Override
        Client client(String appId) {
            return this.stub;
        }
    }

    /**
     * 内存凭据端口。
     */
    private static final class FakeCredentialPort implements ICredentialProviderPort {

        private final Map<String, String> store = new HashMap<>();

        private void put(String ref, String value) {
            this.store.put(ref, value);
        }

        @Override
        public Optional<ResolvedCredentialVO> resolve(CredentialRef ref) {
            String value = this.store.get(ref.value());
            return value == null ? Optional.empty() : Optional.of(new ResolvedCredentialVO(value, "test"));
        }

        @Override
        public CredentialInfoVO describe(CredentialRef ref) {
            return new CredentialInfoVO(this.store.containsKey(ref.value()), Optional.of("test"), true);
        }

        @Override
        public void set(CredentialRef ref, String value) {
            this.store.put(ref.value(), value);
        }

        @Override
        public void unset(CredentialRef ref) {
            this.store.remove(ref.value());
        }
    }
}
