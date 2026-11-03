package net.linlan.dsh.domain.bot.model.aggregate;

import net.linlan.dsh.domain.bot.model.valobj.FeishuConnectionState;
import net.linlan.dsh.domain.bot.model.valobj.FeishuInboundMessage;
import java.time.Instant;

/**
 * 飞书 Bot 聚合根：承载 Bot 配置与运行期连接状态。
 */
public class FeishuBotAggregate {

    /** Bot ID（主键）。 */
    private final String botId;
    /** Bot 展示名。 */
    private String botName;
    /** 飞书应用 app_id。 */
    private final String appId;
    /** 凭据引用键（app_secret 不落库）。 */
    private final String secretRef;
    /** 是否启用。 */
    private boolean enabled;
    /** Agent 基 ID（用于派生会话 agentId）。 */
    private String agentBaseId;
    /** 模型渠道编码（IM 渠道不写入该字段）。 */
    private String channelCode;
    /** Agent 工作目录。 */
    private String cwd;
    /** 群聊是否需要 @ 才处理。 */
    private boolean groupRequireMention;
    /** 运行期连接状态（不落库）。 */
    private FeishuConnectionState connectionState;
    /** 运行期最近错误（不落库）。 */
    private String lastError;
    /** 创建时间。 */
    private Instant createdAt;
    /** 更新时间。 */
    private Instant updatedAt;

    /**
     * 构造 Bot 聚合（不含运行期状态，按 enabled 推导初始连接状态）。
     */
    public FeishuBotAggregate(String botId, String botName, String appId, String secretRef, boolean enabled,
                              String agentBaseId, String channelCode, String cwd, boolean groupRequireMention,
                              Instant createdAt, Instant updatedAt) {
        if (botId == null || botId.isBlank()) {
            throw new IllegalArgumentException("botId 不能为空");
        }
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId 不能为空");
        }
        if (agentBaseId == null || agentBaseId.isBlank()) {
            throw new IllegalArgumentException("agentBaseId 不能为空");
        }
        this.botId = botId;
        this.botName = botName == null || botName.isBlank() ? botId : botName;
        this.appId = appId;
        this.secretRef = secretRef;
        this.enabled = enabled;
        this.agentBaseId = agentBaseId;
        this.channelCode = channelCode;
        this.cwd = cwd;
        this.groupRequireMention = groupRequireMention;
        this.connectionState = enabled ? FeishuConnectionState.CONNECTING : FeishuConnectionState.IDLE;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 由会话 ID 派生 agentId：&lt;agentBaseId&gt;-fs-&lt;chatId 末 16 位&gt;，chatId 不可用时回退 anon。
     *
     * @param chatId 会话 ID
     * @return 派生的 agentId
     */
    public String deriveAgentId(String chatId) {
        String suffix = chatId == null ? "anon" : chatId;
        if (suffix.length() > 16) {
            suffix = suffix.substring(suffix.length() - 16);
        }
        return this.agentBaseId + "-fs-" + suffix;
    }

    /**
     * 判断是否处理该入站消息：单聊恒处理，群聊按 groupRequireMention。
     *
     * @param message 入站消息
     * @return 需要处理返回 true
     */
    public boolean shouldHandle(FeishuInboundMessage message) {
        if (message.isP2P()) {
            return true;
        }
        return !this.groupRequireMention;
    }

    /**
     * 重命名 Bot（空白值忽略）。
     *
     * @param botName 新名称
     */
    public void rename(String botName) {
        if (botName != null && !botName.isBlank()) {
            this.botName = botName;
        }
    }

    /**
     * 更新可变配置（agentBaseId / channelCode / cwd）。
     *
     * @param agentBaseId Agent 基 ID
     * @param channelCode 模型渠道编码
     * @param cwd         Agent 工作目录
     */
    public void updateConfig(String agentBaseId, String channelCode, String cwd) {
        if (agentBaseId != null && !agentBaseId.isBlank()) {
            this.agentBaseId = agentBaseId.trim();
        }
        this.channelCode = channelCode == null || channelCode.isBlank() ? null : channelCode.trim();
        this.cwd = cwd == null || cwd.isBlank() ? null : cwd.trim();
    }

    /** 启用 Bot。 */
    public void enable() {
        this.enabled = true;
    }

    /** 禁用 Bot。 */
    public void disable() {
        this.enabled = false;
    }

    /** 标记连接中。 */
    public void onConnecting() {
        this.connectionState = FeishuConnectionState.CONNECTING;
        this.lastError = null;
    }

    /** 标记已连接。 */
    public void onConnected() {
        this.connectionState = FeishuConnectionState.CONNECTED;
        this.lastError = null;
    }

    /**
     * 标记连接异常。
     *
     * @param message 错误信息
     */
    public void onError(String message) {
        this.connectionState = FeishuConnectionState.ERROR;
        this.lastError = message;
    }

    /** 标记已停止。 */
    public void onStopped() {
        this.connectionState = FeishuConnectionState.IDLE;
        this.lastError = null;
    }

    /** @return Bot ID */
    public String getBotId() {
        return this.botId;
    }

    /** @return Bot 展示名 */
    public String getBotName() {
        return this.botName;
    }

    /** @return 飞书应用 app_id */
    public String getAppId() {
        return this.appId;
    }

    /** @return 凭据引用键 */
    public String getSecretRef() {
        return this.secretRef;
    }

    /** @return 是否启用 */
    public boolean isEnabled() {
        return this.enabled;
    }

    /** @return Agent 基 ID */
    public String getAgentBaseId() {
        return this.agentBaseId;
    }

    /** @return 模型渠道编码 */
    public String getChannelCode() {
        return this.channelCode;
    }

    /** @return Agent 工作目录 */
    public String getCwd() {
        return this.cwd;
    }

    /** @return 群聊是否需 @ */
    public boolean isGroupRequireMention() {
        return this.groupRequireMention;
    }

    /**
     * 设置群聊是否需 @。
     *
     * @param groupRequireMention 需 @ 则 true
     */
    public void setGroupRequireMention(boolean groupRequireMention) {
        this.groupRequireMention = groupRequireMention;
    }

    /** @return 连接状态 */
    public FeishuConnectionState getConnectionState() {
        return this.connectionState;
    }

    /** @return 最近错误 */
    public String getLastError() {
        return this.lastError;
    }

    /** @return 创建时间 */
    public Instant getCreatedAt() {
        return this.createdAt;
    }

    /** @return 更新时间 */
    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    /**
     * 设置更新时间。
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
