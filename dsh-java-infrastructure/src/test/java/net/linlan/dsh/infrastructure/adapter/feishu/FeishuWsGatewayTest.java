package net.linlan.dsh.infrastructure.adapter.feishu;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.EventSender;
import com.lark.oapi.service.im.v1.model.MentionEvent;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.lark.oapi.service.im.v1.model.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.linlan.dsh.domain.bot.model.aggregate.FeishuBotAggregate;
import net.linlan.dsh.domain.bot.model.valobj.FeishuConnectionState;
import net.linlan.dsh.domain.bot.model.valobj.FeishuInboundMessage;
import net.linlan.dsh.domain.credentials.adapter.port.ICredentialProviderPort;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialInfoVO;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialRef;
import net.linlan.dsh.domain.credentials.model.valobj.ResolvedCredentialVO;
import net.linlan.dsh.infrastructure.dao.IFeishuBotDao;
import net.linlan.dsh.infrastructure.dao.po.FeishuBotPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 长连接网关测试：以桩事件驱动入站编排（去重 / @ 判定 / 文本抽取），全程不建立真实 WebSocket。
 */
class FeishuWsGatewayTest {

    private FakeCredentialPort credentials;
    private FakeInboundHandler inbound;
    private FakeDao dao;
    private FeishuWsGateway gateway;

    @BeforeEach
    void setUp() {
        this.credentials = new FakeCredentialPort();
        this.inbound = new FakeInboundHandler();
        this.dao = new FakeDao();
        FeishuMessagingClient messaging = new FeishuMessagingClient(this.credentials, new ObjectMapper());
        this.gateway = new FeishuWsGateway(this.dao, this.inbound, messaging, this.credentials, new ObjectMapper());
    }

    private FeishuBotAggregate bot(boolean enabled, boolean groupRequireMention) {
        return new FeishuBotAggregate("fbot-test", "测试 Bot", "cli_test",
                "FEISHU_BOT_FBOT_TEST_APP_SECRET", enabled, "feishu-agent", null, "/tmp", groupRequireMention,
                Instant.now(), Instant.now());
    }

    private static P2MessageReceiveV1 event(String messageId, String chatId, String chatType, String type,
                                            String content, MentionEvent[] mentions, String openId) {
        EventMessage message = new EventMessage();
        message.setMessageId(messageId);
        message.setChatId(chatId);
        message.setChatType(chatType);
        message.setMessageType(type);
        message.setContent(content);
        message.setMentions(mentions);
        P2MessageReceiveV1Data data = new P2MessageReceiveV1Data();
        data.setMessage(message);
        if (openId != null) {
            UserId id = new UserId();
            id.setOpenId(openId);
            EventSender sender = new EventSender();
            sender.setSenderId(id);
            data.setSender(sender);
        }
        P2MessageReceiveV1 wrapper = new P2MessageReceiveV1();
        wrapper.setEvent(data);
        return wrapper;
    }

    private static MentionEvent mention(String key, String name, String openId) {
        MentionEvent mention = new MentionEvent();
        mention.setKey(key);
        mention.setName(name);
        if (openId != null) {
            UserId id = new UserId();
            id.setOpenId(openId);
            mention.setId(id);
        }
        return mention;
    }

    @Test
    void handlesP2pMessageAndExtractsText() {
        this.gateway.onMessage(this.bot(true, true), event("m1", "oc_1", "p2p", "text",
                "{\"text\":\"你好\"}", null, "ou_1234567890"));

        assertEquals(1, this.inbound.messages.size());
        FeishuInboundMessage message = this.inbound.messages.get(0);
        assertEquals("fbot-test", message.botId());
        assertEquals("m1", message.messageId());
        assertEquals("oc_1", message.chatId());
        assertEquals("你好", message.text());
        assertTrue(message.isP2P());
        assertTrue(message.hasText());
        assertEquals("ou_1234567890", message.senderOpenId());
        assertEquals("用户7890", message.senderName());
    }

    @Test
    void skipsGroupMessageWithoutMentionWhenRequired() {
        this.gateway.onMessage(this.bot(true, true), event("m2", "oc_2", "group", "text",
                "{\"text\":\"大家好\"}", null, "ou_1"));

        assertTrue(this.inbound.messages.isEmpty(), "群聊未 @ 且要求 @ 时应跳过");
    }

    @Test
    void handlesGroupMessageWithMentionAndReplacesPlaceholder() {
        MentionEvent[] mentions = new MentionEvent[]{mention("@_user_1", "张三", "ou_1")};
        this.gateway.onMessage(this.bot(true, true), event("m3", "oc_2", "group", "text",
                "{\"text\":\"@_user_1 帮我重构\"}", mentions, "ou_1"));

        assertEquals(1, this.inbound.messages.size());
        assertEquals("@张三 帮我重构", this.inbound.messages.get(0).text());
        assertEquals("张三", this.inbound.messages.get(0).senderName());
    }

    @Test
    void handlesGroupMessageWhenMentionNotRequired() {
        this.gateway.onMessage(this.bot(true, false), event("m4", "oc_2", "group", "text",
                "{\"text\":\"无需 @\"}", null, "ou_1"));

        assertEquals(1, this.inbound.messages.size());
    }

    @Test
    void deduplicatesRepeatedMessageId() {
        FeishuBotAggregate bot = this.bot(true, true);
        P2MessageReceiveV1 event = event("m5", "oc_1", "p2p", "text", "{\"text\":\"重复投递\"}", null, "ou_1");
        this.gateway.onMessage(bot, event);
        this.gateway.onMessage(bot, event);

        assertEquals(1, this.inbound.messages.size(), "同一 messageId 重复投递应只处理一次");
    }

    @Test
    void flattensPostRichText() {
        String content = "{\"content\":[[{\"tag\":\"text\",\"text\":\"第一行\"},{\"tag\":\"at\",\"user_name\":\"李四\"}],"
                + "[{\"tag\":\"a\",\"text\":\"链接\"},{\"tag\":\"emotion\",\"emoji_type\":\"SMILE\"}]]}";
        this.gateway.onMessage(this.bot(true, true), event("m6", "oc_1", "p2p", "post", content, null, "ou_1"));

        assertEquals(1, this.inbound.messages.size());
        assertEquals("第一行李四\n链接SMILE", this.inbound.messages.get(0).text());
    }

    @Test
    void nonTextMessageFallsBackToNullText() {
        this.gateway.onMessage(this.bot(true, true), event("m7", "oc_1", "p2p", "image",
                "{\"image_key\":\"img_1\"}", null, "ou_1"));

        assertEquals(1, this.inbound.messages.size());
        assertNull(this.inbound.messages.get(0).text());
        assertFalse(this.inbound.messages.get(0).hasText());
    }

    @Test
    void startFailsWithErrorStateWhenSecretMissing() {
        FeishuBotAggregate bot = this.bot(true, true);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> this.gateway.start(bot));

        assertTrue(error.getMessage().contains("缺少 app_secret"), error.getMessage());
        assertEquals(FeishuConnectionState.ERROR, bot.getConnectionState());
        assertTrue(bot.getLastError().contains("未配置 app_secret"), bot.getLastError());
        assertEquals("IDLE", this.gateway.stateOf("fbot-test"), "未建连的 Bot 对外状态应为 IDLE");
    }

    @Test
    void autoStartIsolatesPerBotFailure() {
        this.dao.save(new FeishuBotPO("fbot-a", "A", "cli_a", "REF_A", 1, "feishu-agent", null,
                "/tmp", 1, Instant.now(), Instant.now()));
        this.dao.save(new FeishuBotPO("fbot-b", "B", "cli_b", "REF_B", 1, "feishu-agent", null,
                "/tmp", 1, Instant.now(), Instant.now()));

        assertDoesNotThrow(() -> this.gateway.autoStartEnabledBots(), "单 Bot 失败不得阻断启动");
    }

    @Test
    void unknownBotReportsIdleAndNoError() {
        assertEquals("IDLE", this.gateway.stateOf("fbot-unknown"));
        assertNull(this.gateway.lastErrorOf("fbot-unknown"));
        assertFalse(this.gateway.isConnected("fbot-unknown"));
        assertDoesNotThrow(() -> this.gateway.stop("fbot-unknown"));
        assertDoesNotThrow(() -> this.gateway.destroy());
    }

    /**
     * 记录入站消息的桩处理器。
     */
    private static final class FakeInboundHandler implements net.linlan.dsh.domain.bot.adapter.port.IFeishuInboundHandler {

        private final List<FeishuInboundMessage> messages = new ArrayList<>();

        @Override
        public void handle(FeishuBotAggregate bot, FeishuInboundMessage message) {
            this.messages.add(message);
        }
    }

    /**
     * 内存 DAO 桩。
     */
    private static final class FakeDao implements IFeishuBotDao {

        private final Map<String, FeishuBotPO> store = new LinkedHashMap<>();

        @Override
        public void save(FeishuBotPO po) {
            this.store.put(po.botId(), po);
        }

        @Override
        public Optional<FeishuBotPO> findByBotId(String botId) {
            return Optional.ofNullable(this.store.get(botId));
        }

        @Override
        public List<FeishuBotPO> findAll() {
            return new ArrayList<>(this.store.values());
        }

        @Override
        public void deleteByBotId(String botId) {
            this.store.remove(botId);
        }
    }

    /**
     * 内存凭据端口（默认无密钥，可显式 put）。
     */
    private static final class FakeCredentialPort implements ICredentialProviderPort {

        private final Map<String, String> store = new HashMap<>();

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
