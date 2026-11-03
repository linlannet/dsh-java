package net.linlan.dsh.infrastructure.adapter.feishu;

import net.linlan.dsh.domain.bot.adapter.port.IFeishuBotConnectionPort;
import net.linlan.dsh.domain.bot.adapter.port.IFeishuInboundHandler;
import net.linlan.dsh.domain.bot.model.aggregate.FeishuBotAggregate;
import net.linlan.dsh.domain.bot.model.valobj.FeishuConnectionState;
import net.linlan.dsh.domain.bot.model.valobj.FeishuInboundMessage;
import net.linlan.dsh.domain.bot.service.FeishuEventDeduplicator;
import net.linlan.dsh.domain.credentials.adapter.port.ICredentialProviderPort;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialRef;
import net.linlan.dsh.domain.credentials.model.valobj.ResolvedCredentialVO;
import net.linlan.dsh.infrastructure.dao.IFeishuBotDao;
import net.linlan.dsh.infrastructure.dao.po.FeishuBotPO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.MentionEvent;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.ws.Client;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * 飞书长连接网关：管理各 Bot 的 WebSocket 长连接、订阅 {@code im.message.receive_v1} 入站事件并桥接待处理。
 */
public class FeishuWsGateway implements IFeishuBotConnectionPort, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(FeishuWsGateway.class);
    private static final long AWAIT_READY_MS = 15000L;

    /** 数据访问对象。 */
    private final IFeishuBotDao feishuBotDao;
    /** 入站处理端口。 */
    private final IFeishuInboundHandler inboundHandler;
    /** 出站消息适配器。 */
    private final FeishuMessagingClient messagingClient;
    /** 凭据端口。 */
    private final ICredentialProviderPort credentialPort;
    /** JSON 解析器。 */
    private final ObjectMapper objectMapper;
    /** 事件去重器。 */
    private final FeishuEventDeduplicator deduplicator = new FeishuEventDeduplicator();
    /** botId -> 已建立的 SDK 客户端。 */
    private final Map<String, Client> connections = new ConcurrentHashMap<>();
    /** botId -> 活跃 Bot 聚合。 */
    private final Map<String, FeishuBotAggregate> activeBots = new ConcurrentHashMap<>();
    /** 建连线程池。 */
    private final ExecutorService connectExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "feishu-ws-connector");
        t.setDaemon(true);
        return t;
    });

    /**
     * 构造长连接网关。
     *
     * @param feishuBotDao    数据访问对象
     * @param inboundHandler  入站处理端口
     * @param messagingClient 出站消息适配器
     * @param credentialPort  凭据端口
     * @param objectMapper    JSON 解析器
     */
    public FeishuWsGateway(IFeishuBotDao feishuBotDao, IFeishuInboundHandler inboundHandler,
                           FeishuMessagingClient messagingClient, ICredentialProviderPort credentialPort,
                           ObjectMapper objectMapper) {
        this.feishuBotDao = feishuBotDao;
        this.inboundHandler = inboundHandler;
        this.messagingClient = messagingClient;
        this.credentialPort = credentialPort;
        this.objectMapper = objectMapper;
    }

    /**
     * 应用就绪后拉起已启用的 Bot 长连接（逐 Bot 隔离异常）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void autoStartEnabledBots() {
        this.feishuBotDao.findAll().stream()
                .filter(po -> po.enabled() != null && po.enabled() == 1)
                .forEach(po -> this.feishuBotDao.findByBotId(po.botId()).ifPresent(p -> {
                    FeishuBotAggregate bot = this.toAggregate(p);
                    if (!bot.isEnabled()) {
                        return;
                    }
                    try {
                        this.start(bot);
                    } catch (Exception e) {
                        log.warn("[Feishu] 启动 Bot {} 长连接失败: {}", bot.getBotId(), e.getMessage());
                    }
                }));
    }

    @Override
    public void start(final FeishuBotAggregate bot) {
        if (this.connections.containsKey(bot.getBotId())) {
            return;
        }
        Optional<String> secret = this.credentialPort.resolve(CredentialRef.of(bot.getSecretRef()))
                .map(ResolvedCredentialVO::value);
        if (secret.isEmpty() || secret.get().isBlank()) {
            bot.onError("凭据 " + bot.getSecretRef() + " 未配置 app_secret");
            throw new IllegalStateException("Bot " + bot.getBotId() + " 缺少 app_secret（凭据 " + bot.getSecretRef() + "）");
        }
        if (!this.messagingClient.register(bot.getAppId(), bot.getSecretRef())) {
            bot.onError("注册飞书应用失败");
            throw new IllegalStateException("Bot " + bot.getBotId() + " 注册飞书应用失败（secret 解析异常）");
        }
        EventDispatcher dispatcher = EventDispatcher.newBuilder("", "")
                .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                    @Override
                    public void handle(P2MessageReceiveV1 event) {
                        onMessage(bot, event);
                    }
                })
                .build();
        Client ws = new Client.Builder(bot.getAppId(), secret.get())
                .eventHandler(dispatcher)
                .autoReconnect(true)
                .build();
        this.connections.put(bot.getBotId(), ws);
        this.activeBots.put(bot.getBotId(), bot);
        bot.onConnecting();
        log.info("[Feishu] Bot {}（app={}）正在建立长连接…", bot.getBotId(), bot.getAppId());
        this.connectExecutor.submit(() -> {
            try {
                ws.start();
                ws.awaitReady(AWAIT_READY_MS);
                bot.onConnected();
                log.info("[Feishu] Bot {} 长连接已在线", bot.getBotId());
            } catch (Exception e) {
                if (this.connections.get(bot.getBotId()) != ws) {
                    return;
                }
                bot.onError(e.getMessage());
                log.warn("[Feishu] Bot {} 长连接异常: {}", bot.getBotId(), e.getMessage());
            }
        });
    }

    @Override
    public void stop(String botId) {
        Client ws = this.connections.remove(botId);
        FeishuBotAggregate bot = this.activeBots.remove(botId);
        if (bot != null) {
            this.messagingClient.unregister(bot.getAppId());
        }
        if (ws != null) {
            ws.close();
        }
        if (bot != null) {
            bot.onStopped();
        }
        log.info("[Feishu] Bot {} 长连接已停止", botId);
    }

    @Override
    public boolean isConnected(String botId) {
        Client ws = this.connections.get(botId);
        FeishuBotAggregate bot = this.activeBots.get(botId);
        return ws != null && bot != null && bot.getConnectionState().isOnline();
    }

    @Override
    public String stateOf(String botId) {
        FeishuBotAggregate bot = this.activeBots.get(botId);
        return bot == null ? FeishuConnectionState.IDLE.name() : bot.getConnectionState().name();
    }

    @Override
    public String lastErrorOf(String botId) {
        FeishuBotAggregate bot = this.activeBots.get(botId);
        return bot == null ? null : bot.getLastError();
    }

    @Override
    public void destroy() {
        this.connections.keySet().forEach(this::stop);
        this.connectExecutor.shutdownNow();
    }

    /**
     * 处理入站事件：去重 → 群聊 @ 判定 → 文本抽取 → 桥接处理。
     *
     * @param bot   Bot 聚合
     * @param event 飞书入站事件
     */
    void onMessage(FeishuBotAggregate bot, P2MessageReceiveV1 event) {
        try {
            if (event.getEvent() == null || event.getEvent().getMessage() == null) {
                return;
            }
            EventMessage msg = event.getEvent().getMessage();
            if (this.deduplicator.isDuplicate(msg.getMessageId())) {
                return;
            }
            boolean mentioned = msg.getMentions() != null && msg.getMentions().length > 0;
            if (!"p2p".equals(msg.getChatType()) && bot.isGroupRequireMention() && !mentioned) {
                return;
            }
            String openId = null;
            if (event.getEvent().getSender() != null && event.getEvent().getSender().getSenderId() != null) {
                openId = event.getEvent().getSender().getSenderId().getOpenId();
            }
            String text = this.extractText(msg);
            FeishuInboundMessage message = new FeishuInboundMessage(bot.getBotId(), msg.getMessageId(), msg.getChatId(),
                    msg.getChatType(), msg.getMessageType(), text, openId, this.senderLabel(msg, openId));
            this.inboundHandler.handle(bot, message);
        } catch (Exception e) {
            log.error("[Feishu] 处理入站消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 抽取文本（支持 text 与 post 富文本）。
     *
     * @param msg 飞书事件消息
     * @return 抽取后的文本，无法抽取时返回 null
     */
    private String extractText(EventMessage msg) {
        String type = msg.getMessageType();
        String content = msg.getContent();
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            JsonNode root = this.objectMapper.readTree(content);
            String raw = null;
            if ("text".equals(type)) {
                raw = root.path("text").asText(null);
            } else if ("post".equals(type)) {
                raw = this.flattenPost(root);
            }
            if (raw == null) {
                return null;
            }
            return this.replaceMentions(raw, msg);
        } catch (Exception e) {
            log.warn("[Feishu] 解析消息内容失败 type={}: {}", type, e.getMessage());
            return null;
        }
    }

    /**
     * 拼接 post 富文本。
     *
     * @param root post 内容 JSON
     * @return 拼接文本
     */
    private String flattenPost(JsonNode root) {
        JsonNode contentNode = root.path("content");
        if (!contentNode.isArray() || contentNode.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode line : contentNode) {
            if (!line.isArray()) {
                continue;
            }
            for (JsonNode seg : line) {
                String tag = seg.path("tag").asText("");
                if ("text".equals(tag) || "a".equals(tag)) {
                    sb.append(seg.path("text").asText(""));
                } else if ("at".equals(tag)) {
                    sb.append(seg.path("user_name").asText("@"));
                } else if ("emotion".equals(tag)) {
                    sb.append(seg.path("emoji_type").asText(""));
                }
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * 将 @ 占位符替换为 @名称。
     *
     * @param text 原始文本
     * @param msg  飞书事件消息
     * @return 替换后的文本
     */
    private String replaceMentions(String text, EventMessage msg) {
        MentionEvent[] mentions = msg.getMentions();
        if (mentions == null || mentions.length == 0) {
            return text;
        }
        for (MentionEvent mention : mentions) {
            if (mention.getKey() == null) {
                continue;
            }
            String name = mention.getName() == null ? mention.getKey() : mention.getName();
            text = text.replace(mention.getKey(), "@" + name);
        }
        return text;
    }

    /**
     * 推导发送者展示名。
     *
     * @param msg    飞书事件消息
     * @param openId 发送者 open_id
     * @return 展示名
     */
    private String senderLabel(EventMessage msg, String openId) {
        if (msg.getMentions() != null) {
            for (MentionEvent mention : msg.getMentions()) {
                if (openId == null || mention.getId() == null) {
                    continue;
                }
                if (openId.equals(mention.getId().getOpenId())) {
                    return mention.getName();
                }
            }
        }
        return openId == null ? null : "用户" + openId.substring(Math.max(0, openId.length() - 4));
    }

    /**
     * PO 转聚合。
     *
     * @param po 持久化对象
     * @return Bot 聚合
     */
    private FeishuBotAggregate toAggregate(FeishuBotPO po) {
        FeishuBotAggregate aggregate = new FeishuBotAggregate(po.botId(), po.botName(), po.appId(), po.secretRef(),
                po.enabled() != null && po.enabled() == 1, po.agentBaseId(), po.channelCode(), po.cwd(),
                po.groupRequireMention() == null || po.groupRequireMention() == 1, po.createdAt(), po.updatedAt());
        if (!aggregate.isEnabled()) {
            aggregate.onStopped();
        }
        return aggregate;
    }
}