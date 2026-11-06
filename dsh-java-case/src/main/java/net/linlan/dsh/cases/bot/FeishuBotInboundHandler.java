package net.linlan.dsh.cases.bot;

import net.linlan.dsh.api.dto.AgentMessageRequestDTO;
import net.linlan.dsh.api.dto.AgentMessageResponseDTO;
import net.linlan.dsh.api.dto.AgentToolCallEventResponseDTO;
import net.linlan.dsh.cases.agent.IAgentUseCase;
import net.linlan.dsh.domain.bot.adapter.port.IFeishuInboundHandler;
import net.linlan.dsh.domain.bot.adapter.port.IFeishuMessagingPort;
import net.linlan.dsh.domain.bot.model.aggregate.FeishuBotAggregate;
import net.linlan.dsh.domain.bot.model.valobj.FeishuInboundMessage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 飞书入站桥接处理器：将入站消息投递到 Agent 会话，并把流式增量回发为飞书消息。
 * <p>由 {@code FeishuBeanConfig#feishuInboundHandler()} 在飞书开关
 * （{@code dsh.feishu.enabled}，缺省 true）开启时显式装配。
 */
public class FeishuBotInboundHandler implements IFeishuInboundHandler {

    private static final Logger log = LoggerFactory.getLogger(FeishuBotInboundHandler.class);
    private static final long FLUSH_INTERVAL_MS = 1500L;
    private static final int STREAM_PREVIEW_LIMIT = 4000;
    private static final int FINAL_IN_PLACE_LIMIT = 4000;
    private static final int CARD_LIMIT = 28000;

    /** Agent 交互用例。 */
    @Autowired
    private IAgentUseCase agentUseCase;
    /** 出站消息端口。 */
    @Autowired
    private IFeishuMessagingPort messagingPort;

    private final Map<String, ExecutorService> botExecutors = new ConcurrentHashMap<>();

    @Override
    public void handle(FeishuBotAggregate bot, FeishuInboundMessage message) {
        this.botExecutors.computeIfAbsent(bot.getBotId(), id -> Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "feishu-bot-" + id);
            t.setDaemon(true);
            return t;
        })).submit(() -> {
            try {
                this.process(bot, message);
            } catch (Exception e) {
                log.error("[Feishu] Bot {} 处理消息失败 msgId={}: {}", bot.getBotId(), message.messageId(), e.getMessage(), e);
                this.safeReplyError(bot, message, e.getMessage());
            }
        });
    }

    /**
     * 处理单条入站消息：占位消息 → Agent 流式调用 → 节流编辑 → 定稿/卡片降级。
     *
     * @param bot     Bot 聚合
     * @param message 入站消息
     */
    private void process(FeishuBotAggregate bot, FeishuInboundMessage message) {
        if (!message.hasText()) {
            this.messagingPort.sendText(bot.getAppId(), message.chatId(),
                    "目前我只能看懂文字消息，图片/语音/文件暂时处理不了～");
            return;
        }
        String chatId = message.chatId();
        String agentId = bot.deriveAgentId(chatId);
        String userText = this.buildUserText(bot, message);
        String placeholder = this.messagingPort.sendText(bot.getAppId(), chatId, "⏳ 正在思考…");
        StreamReply reply = new StreamReply(bot.getAppId(), chatId, placeholder);
        AgentMessageRequestDTO request = new AgentMessageRequestDTO(agentId, bot.getChannelCode(), null, bot.getCwd(),
                userText, null, "AUTO_APPROVE", null, null);
        AgentMessageResponseDTO result;
        try {
            result = this.agentUseCase.sendMessageStreaming(request, reply::onDelta, null, reply::onToolCall, reply::onFinish);
        } catch (IllegalStateException e) {
            this.messagingPort.updateText(bot.getAppId(), placeholder, "⏳ 上一条消息还在处理中，请等它完成后再发～");
            return;
        }
        String finalText = result == null || result.error() != null ? null : reply.text();
        if (finalText == null || finalText.isBlank()) {
            String error = result == null ? "空响应" : result.error();
            if (error == null || error.isBlank()) {
                error = reply.text().isBlank() ? "模型没有返回内容" : reply.text();
            }
            reply.finalizeWith("⚠️ " + error);
            return;
        }
        if (finalText.length() <= FINAL_IN_PLACE_LIMIT) {
            reply.finalizeWith(finalText);
        } else {
            String cardText = finalText.length() > CARD_LIMIT
                    ? finalText.substring(0, CARD_LIMIT) + "\n\n…（内容过长已截断）"
                    : finalText;
            try {
                this.messagingPort.sendMarkdownCard(bot.getAppId(), chatId, cardText);
                reply.finalizeWith("✅ 回复已生成（完整内容见下方卡片）");
            } catch (Exception e) {
                log.warn("[Feishu] 卡片发送失败，退化为占位消息截断展示: {}", e.getMessage());
                reply.finalizeWith(finalText.substring(0, FINAL_IN_PLACE_LIMIT) + "\n\n…（内容过长已截断）");
            }
        }
    }

    /**
     * 组装投递给 Agent 的用户文本：单聊原样，群聊回填发送者。
     *
     * @param bot     Bot 聚合
     * @param message 入站消息
     * @return 用户文本
     */
    private String buildUserText(FeishuBotAggregate bot, FeishuInboundMessage message) {
        if (message.isP2P()) {
            return message.text();
        }
        String sender = message.senderName() == null ? "群成员" : message.senderName();
        return "[" + sender + "] " + message.text();
    }

    /**
     * 兜底错误回复（仅群内 @ 或单聊）。
     *
     * @param bot     Bot 聚合
     * @param message 入站消息
     * @param errorText 错误信息
     */
    private void safeReplyError(FeishuBotAggregate bot, FeishuInboundMessage message, String errorText) {
        try {
            if (message.hasText() || message.isP2P()) {
                this.messagingPort.sendText(bot.getAppId(), message.chatId(), "⚠️ 处理失败：" + errorText);
            }
        } catch (Exception ignore) {
            // 忽略兜底回复异常
        }
    }

    /**
     * 流式占位消息编辑器：节流刷新 + 工具行 + 定稿。
     */
    private class StreamReply {
        /** 飞书应用 app_id。 */
        private final String appId;
        /** 会话 ID。 */
        private final String chatId;
        /** 占位消息 ID。 */
        private final String placeholder;
        /** 增量缓冲。 */
        private final StringBuilder buffer = new StringBuilder();
        /** 上次刷新时间戳。 */
        private volatile long lastFlushAt = 0L;
        /** 当前工具行。 */
        private volatile String toolLine = null;
        /** 是否已定稿。 */
        private volatile boolean finished = false;

        StreamReply(String appId, String chatId, String placeholder) {
            this.appId = appId;
            this.chatId = chatId;
            this.placeholder = placeholder;
        }

        /**
         * 接收文本增量并按间隔节流刷新。
         *
         * @param delta 文本增量
         */
        void onDelta(String delta) {
            if (delta == null || this.finished) {
                return;
            }
            synchronized (this.buffer) {
                this.buffer.append(delta);
            }
            this.maybeFlush();
        }

        /**
         * 接收工具调用事件并刷新工具行。
         *
         * @param event 工具调用事件
         */
        void onToolCall(AgentToolCallEventResponseDTO event) {
            if (event == null || this.finished) {
                return;
            }
            boolean end = "result".equalsIgnoreCase(event.phase()) || "done".equalsIgnoreCase(event.phase())
                    || event.result() != null;
            this.toolLine = end ? "✅ " + event.toolName() + " 完成" : "🔧 执行工具：" + event.toolName();
            this.flush(true);
        }

        /** 流结束回调（占位保留，定稿由 finalizeWith 完成）。 */
        void onFinish() {
            // no-op
        }

        /**
         * 当前累积文本。
         *
         * @return 文本内容
         */
        String text() {
            synchronized (this.buffer) {
                return this.buffer.toString();
            }
        }

        /**
         * 定稿：就地更新占位消息。
         *
         * @param text 最终文本
         */
        void finalizeWith(String text) {
            this.finished = true;
            FeishuBotInboundHandler.this.messagingPort.updateText(this.appId, this.placeholder, text);
        }

        /** 按节流间隔决定是否刷新。 */
        private void maybeFlush() {
            long now = System.currentTimeMillis();
            if (now - this.lastFlushAt < FLUSH_INTERVAL_MS) {
                return;
            }
            this.flush(false);
        }

        /**
         * 刷新占位消息预览。
         *
         * @param force 是否强制刷新
         */
        private void flush(boolean force) {
            if (this.finished) {
                return;
            }
            this.lastFlushAt = System.currentTimeMillis();
            String body;
            synchronized (this.buffer) {
                body = this.buffer.toString();
            }
            StringBuilder preview = new StringBuilder();
            if (this.toolLine != null) {
                preview.append(this.toolLine).append("\n\n");
            }
            if (body.length() > STREAM_PREVIEW_LIMIT) {
                preview.append(body, 0, STREAM_PREVIEW_LIMIT).append("\n\n…（生成中）");
            } else {
                preview.append(body);
            }
            String snapshot = preview.toString();
            FeishuBotInboundHandler.this.messagingPort.updateText(this.appId, this.placeholder,
                    snapshot.isBlank() ? "⏳ 正在思考…" : snapshot);
        }
    }
}