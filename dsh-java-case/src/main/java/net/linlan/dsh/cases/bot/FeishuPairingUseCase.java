package net.linlan.dsh.cases.bot;

import net.linlan.dsh.api.condition.FeishuEnabledCondition;
import net.linlan.dsh.api.dto.FeishuPairingDTO;
import net.linlan.dsh.api.dto.FeishuPairingStartDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.domain.bot.adapter.port.IFeishuBotConnectionPort;
import net.linlan.dsh.domain.bot.adapter.port.IFeishuPairingPort;
import net.linlan.dsh.domain.bot.adapter.repository.IFeishuBotRepository;
import net.linlan.dsh.domain.bot.model.aggregate.FeishuBotAggregate;
import net.linlan.dsh.domain.bot.model.valobj.FeishuPairingBeginVO;
import net.linlan.dsh.domain.bot.model.valobj.FeishuPairingPollVO;
import net.linlan.dsh.domain.credentials.adapter.port.ICredentialProviderPort;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialRef;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * 飞书一键配对用例：设备授权 begin/poll 会话管理与落库上线。
 */
@Service
@Conditional(FeishuEnabledCondition.class)
public class FeishuPairingUseCase {

    private static final Logger log = LoggerFactory.getLogger(FeishuPairingUseCase.class);
    private static final Pattern BOT_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}$");
    private static final int MAX_NETWORK_ERRORS = 20;

    /** 配对端口。 */
    @Autowired
    private IFeishuPairingPort pairingPort;
    /** Bot 仓储端口。 */
    @Autowired
    private IFeishuBotRepository botRepository;
    /** 长连接端口。 */
    @Autowired
    private IFeishuBotConnectionPort connectionPort;
    /** 凭据端口。 */
    @Autowired
    private ICredentialProviderPort credentialPort;

    private final Map<String, PairingSession> sessions = new ConcurrentHashMap<>();

    /**
     * 发起一键配对。
     *
     * @param request 配对入参
     * @return 配对状态
     */
    public Response<FeishuPairingDTO> startPairing(FeishuPairingStartDTO request) {
        String botName = request == null || request.botName() == null || request.botName().isBlank()
                ? "DeepSeek Bot" : request.botName().trim();
        String agentBaseId = request == null || request.agentBaseId() == null || request.agentBaseId().isBlank()
                ? "feishu-agent" : request.agentBaseId().trim().toLowerCase();
        try {
            FeishuPairingBeginVO begin = this.pairingPort.begin(botName,
                    "手机上对话电脑端 Harness Agent，支持工具调用与流式回复");
            PairingSession session = new PairingSession(botName, agentBaseId, begin);
            this.sessions.put(session.pairingId, session);
            Thread poller = new Thread(session::run, "feishu-pairing-" + session.pairingId);
            poller.setDaemon(true);
            poller.start();
            log.info("[UseCase] 一键绑定会话 {} 已创建（app={}）", session.pairingId, botName);
            return Response.success(session.toDTO());
        } catch (Exception e) {
            return Response.internalError("发起一键绑定失败：" + e.getMessage());
        }
    }

    /**
     * 查询配对状态。
     *
     * @param pairingId 配对会话 ID
     * @return 配对状态
     */
    public Response<FeishuPairingDTO> pairingStatus(String pairingId) {
        PairingSession session = this.sessions.get(pairingId);
        if (session == null) {
            return Response.invalidArgument("绑定会话不存在或已过期: " + pairingId);
        }
        return Response.success(session.toDTO());
    }

    /**
     * 取消配对。
     *
     * @param pairingId 配对会话 ID
     * @return 空响应
     */
    public Response<Void> cancelPairing(String pairingId) {
        PairingSession session = this.sessions.remove(pairingId);
        if (session != null) {
            session.cancel();
        }
        return Response.success(null);
    }

    /**
     * 单次配对会话：守护线程轮询授权结果，成功后建 Bot 并上线。
     */
    private class PairingSession {
        /** 配对会话 ID。 */
        final String pairingId = UUID.randomUUID().toString().substring(0, 8);
        /** 期望的 Bot 名称。 */
        final String botName;
        /** 期望的 Agent 基 ID。 */
        final String agentBaseId;
        /** 扫码授权链接。 */
        final String url;
        /** 用户码。 */
        final String userCode;
        /** 设备码。 */
        final String deviceCode;
        /** 过期时间戳（毫秒）。 */
        final long expireAt;
        /** 轮询间隔（毫秒）。 */
        volatile int intervalMs;
        /** 轮询基址。 */
        volatile String baseUrl = null;
        /** 状态。 */
        volatile String status = "WAITING";
        /** 错误信息。 */
        volatile String error = null;
        /** 绑定成功后创建的 Bot ID。 */
        volatile String createdBotId = null;
        /** 绑定成功后的 app_id。 */
        volatile String appId = null;
        /** 是否已取消。 */
        volatile boolean cancelled = false;

        PairingSession(String botName, String agentBaseId, FeishuPairingBeginVO begin) {
            this.botName = botName;
            this.agentBaseId = agentBaseId;
            this.url = begin.url();
            this.userCode = begin.userCode();
            this.deviceCode = begin.deviceCode();
            this.expireAt = System.currentTimeMillis() + begin.expireInSec() * 1000L;
            this.intervalMs = begin.intervalSec() * 1000;
        }

        /**
         * 轮询主循环。
         */
        void run() {
            int networkErrors = 0;
            boolean domainSwitched = false;
            while (!this.cancelled && System.currentTimeMillis() < this.expireAt) {
                FeishuPairingPollVO result;
                if (this.baseUrl == null) {
                    this.baseUrl = "https://accounts.feishu.cn";
                }
                try {
                    result = FeishuPairingUseCase.this.pairingPort.poll(this.baseUrl, this.deviceCode);
                } catch (Exception e) {
                    if (++networkErrors > MAX_NETWORK_ERRORS) {
                        this.fail("轮询网络异常：" + e.getMessage());
                        return;
                    }
                    result = new FeishuPairingPollVO(null, null, null, null, "authorization_pending", null);
                }
                if (this.cancelled) {
                    return;
                }
                if ("lark".equals(result.tenantBrand()) && !domainSwitched) {
                    this.baseUrl = FeishuPairingUseCase.this.pairingPort.larkBaseUrl();
                    domainSwitched = true;
                    log.info("[UseCase] 绑定会话 {} 检测到 Lark 租户，切换轮询域名", this.pairingId);
                    continue;
                }
                if (result.succeeded()) {
                    this.complete(result);
                    return;
                }
                switch (result.error() == null ? "" : result.error()) {
                    case "authorization_pending":
                        break;
                    case "slow_down":
                        this.intervalMs += 5000;
                        break;
                    case "access_denied":
                        this.fail("用户拒绝了授权");
                        return;
                    case "expired_token":
                        this.status = "EXPIRED";
                        this.error = "绑定会话已过期，请重新发起";
                        return;
                    default:
                        this.fail(result.errorDescription() == null ? result.error() : result.errorDescription());
                        return;
                }
                try {
                    Thread.sleep(this.intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (!this.cancelled && "WAITING".equals(this.status)) {
                this.status = "EXPIRED";
                this.error = "绑定会话已过期，请重新发起";
            }
        }

        /**
         * 授权成功：写密钥、建 Bot、上线。
         *
         * @param result 轮询结果
         */
        private void complete(FeishuPairingPollVO result) {
            try {
                String botId = this.deriveBotId(this.botName);
                String secretRef = "FEISHU_BOT_" + botId.toUpperCase().replace('-', '_') + "_APP_SECRET";
                FeishuPairingUseCase.this.credentialPort.set(CredentialRef.of(secretRef), result.clientSecret());
                FeishuBotAggregate bot = new FeishuBotAggregate(botId, this.botName, result.clientId(), secretRef, true,
                        this.agentBaseId, null, null, true, Instant.now(), Instant.now());
                FeishuPairingUseCase.this.botRepository.save(bot);
                try {
                    FeishuPairingUseCase.this.connectionPort.start(bot);
                } catch (Exception e) {
                    log.warn("[UseCase] 绑定完成但长连接启动失败（可手动启用重试）: {}", e.getMessage());
                }
                this.appId = result.clientId();
                this.createdBotId = botId;
                this.status = "SAVED";
                log.info("[UseCase] 一键绑定完成：Bot {}（app={}）已创建并上线", botId, result.clientId());
            } catch (Exception e) {
                this.fail("绑定成功但自动建 Bot 失败：" + e.getMessage());
            }
        }

        /**
         * 由名称派生唯一 botId。
         *
         * @param name Bot 名称
         * @return botId
         */
        private String deriveBotId(String name) {
            String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
            String botId = base.length() >= 3 ? base : "fbot";
            if (!BOT_ID_PATTERN.matcher(botId).matches()) {
                botId = "fbot";
            }
            String candidate = botId;
            int seq = 1;
            while (FeishuPairingUseCase.this.botRepository.findByBotId(candidate).isPresent()) {
                candidate = botId + "-" + seq++;
            }
            return candidate;
        }

        /**
         * 标记失败。
         *
         * @param message 错误信息
         */
        private void fail(String message) {
            this.status = "FAILED";
            this.error = message;
            log.warn("[UseCase] 绑定会话 {} 失败: {}", this.pairingId, message);
        }

        /** 取消会话。 */
        void cancel() {
            this.cancelled = true;
            this.status = "CANCELLED";
        }

        /**
         * 输出状态 DTO。
         *
         * @return 配对状态
         */
        FeishuPairingDTO toDTO() {
            long remain = Math.max(0L, (this.expireAt - System.currentTimeMillis()) / 1000L);
            return new FeishuPairingDTO(this.pairingId, this.status, this.url, this.userCode, remain,
                    this.createdBotId, this.appId, this.error);
        }
    }
}