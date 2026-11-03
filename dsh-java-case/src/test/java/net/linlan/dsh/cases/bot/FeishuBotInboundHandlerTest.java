package net.linlan.dsh.cases.bot;

import net.linlan.dsh.api.dto.AgentMessageRequestDTO;
import net.linlan.dsh.api.dto.AgentMessageResponseDTO;
import net.linlan.dsh.api.dto.AgentToolCallEventResponseDTO;
import net.linlan.dsh.cases.agent.IAgentUseCase;
import net.linlan.dsh.domain.bot.adapter.port.IFeishuMessagingPort;
import net.linlan.dsh.domain.bot.model.aggregate.FeishuBotAggregate;
import net.linlan.dsh.domain.bot.model.valobj.FeishuInboundMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 飞书入站桥接与流式回发用例单元测试（使用假 Agent 与假消息端口，同步驱动 process）。
 */
class FeishuBotInboundHandlerTest {

    private FeishuBotInboundHandler handler;
    private FakeAgent agent;
    private FakeMessaging messaging;

    @BeforeEach
    void setUp() throws Exception {
        handler = new FeishuBotInboundHandler();
        agent = new FakeAgent();
        messaging = new FakeMessaging();
        inject(handler, "agentUseCase", agent);
        inject(handler, "messagingPort", messaging);
    }

    private FeishuBotAggregate bot() {
        return new FeishuBotAggregate("fbot-x", "Bot", "cli", "ref", false, "base", null, null, true, null, null);
    }

    private FeishuInboundMessage msg(String text) {
        return new FeishuInboundMessage("fbot-x", "m1", "c1", "p2p", "text", text, null, null);
    }

    private void invokeProcess(FeishuBotAggregate bot, FeishuInboundMessage message) throws Exception {
        Method m = FeishuBotInboundHandler.class.getDeclaredMethod("process", FeishuBotAggregate.class, FeishuInboundMessage.class);
        m.setAccessible(true);
        m.invoke(handler, bot, message);
    }

    @Test
    void nonTextMessageRepliesWithFallback() throws Exception {
        invokeProcess(bot(), new FeishuInboundMessage("fbot-x", "m", "c1", "p2p", "image", null, null, null));
        assertEquals(1, messaging.texts.size());
        assertTrue(messaging.texts.get(0).contains("只能看懂文字消息"));
    }

    @Test
    void shortReplyFinalizesPlaceholderInPlace() throws Exception {
        agent.replyDelta = "hello world";
        invokeProcess(bot(), msg("hi"));

        assertEquals("⏳ 正在思考…", messaging.texts.get(0));
        assertTrue(messaging.updates.size() >= 1);
        assertEquals("hello world", messaging.updates.get(messaging.updates.size() - 1).text);
        assertTrue(messaging.cards.isEmpty());
    }

    @Test
    void longReplyUsesMarkdownCard() throws Exception {
        agent.replyDelta = "x".repeat(5000);
        invokeProcess(bot(), msg("hi"));

        assertEquals(1, messaging.cards.size());
        assertTrue(messaging.updates.get(messaging.updates.size() - 1).text.contains("回复已生成"));
    }

    @Test
    void errorResponseYieldsWarning() throws Exception {
        agent.error = "boom";
        agent.replyDelta = null;
        invokeProcess(bot(), msg("hi"));

        assertTrue(messaging.updates.get(messaging.updates.size() - 1).text.startsWith("⚠️"));
    }

    @Test
    void emptyReplyYieldsNoContentWarning() throws Exception {
        agent.replyDelta = null;
        invokeProcess(bot(), msg("hi"));

        assertTrue(messaging.updates.get(messaging.updates.size() - 1).text.contains("模型没有返回内容"));
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    static class FakeAgent implements IAgentUseCase {
        String replyDelta = "ok";
        String error = null;

        @Override
        public AgentMessageResponseDTO sendMessage(AgentMessageRequestDTO command) {
            return null;
        }

        @Override
        public AgentMessageResponseDTO sendMessageStreaming(AgentMessageRequestDTO command, Consumer<String> deltaSink,
                                                            Consumer<String> reasoningSink,
                                                            Consumer<AgentToolCallEventResponseDTO> toolCallSink,
                                                            Runnable finishSink) {
            if (replyDelta != null) {
                deltaSink.accept(replyDelta);
            }
            finishSink.run();
            return new AgentMessageResponseDTO(command.agentId(), "s1", "idle", List.of(), List.of(), error);
        }

        @Override
        public String getAgentStatus(String agentId) {
            return "idle";
        }

        @Override
        public void cancelAgent(String agentId) {
        }
    }

    static class FakeMessaging implements IFeishuMessagingPort {
        final List<String> texts = new ArrayList<>();
        final List<Update> updates = new ArrayList<>();
        final List<String> cards = new ArrayList<>();

        @Override
        public String sendText(String appId, String chatId, String text) {
            texts.add(text);
            return "ph-" + texts.size();
        }

        @Override
        public boolean updateText(String appId, String messageId, String text) {
            updates.add(new Update(messageId, text));
            return true;
        }

        @Override
        public String sendMarkdownCard(String appId, String chatId, String markdown) {
            cards.add(markdown);
            return "card-1";
        }
    }

    record Update(String messageId, String text) {
    }
}