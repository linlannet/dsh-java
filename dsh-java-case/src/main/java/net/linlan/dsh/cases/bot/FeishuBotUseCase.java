package net.linlan.dsh.cases.bot;

import net.linlan.dsh.api.IBotApi;
import net.linlan.dsh.api.condition.FeishuEnabledCondition;
import net.linlan.dsh.api.dto.FeishuBotDTO;
import net.linlan.dsh.api.dto.FeishuBotSaveDTO;
import net.linlan.dsh.api.dto.FeishuPairingDTO;
import net.linlan.dsh.api.dto.FeishuPairingStartDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.domain.bot.adapter.port.IFeishuBotConnectionPort;
import net.linlan.dsh.domain.bot.adapter.repository.IFeishuBotRepository;
import net.linlan.dsh.domain.bot.model.aggregate.FeishuBotAggregate;
import net.linlan.dsh.domain.bot.model.valobj.FeishuConnectionState;
import net.linlan.dsh.domain.credentials.adapter.port.ICredentialProviderPort;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialRef;
import net.linlan.dsh.domain.credentials.model.valobj.ResolvedCredentialVO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * 飞书 Bot 生命周期用例：Bot 的增删改查、启停与连接测试，并委托一键配对本例。
 */
@Service
@Conditional(FeishuEnabledCondition.class)
public class FeishuBotUseCase implements IBotApi {

    private static final Logger log = LoggerFactory.getLogger(FeishuBotUseCase.class);
    private static final Pattern BOT_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}$");

    /** Bot 仓储端口。 */
    @Autowired
    private IFeishuBotRepository botRepository;
    /** 长连接端口。 */
    @Autowired
    private IFeishuBotConnectionPort connectionPort;
    /** 凭据端口。 */
    @Autowired
    private ICredentialProviderPort credentialPort;
    /** 一键配对用例。 */
    @Autowired
    private FeishuPairingUseCase pairingUseCase;

    @Override
    public Response<FeishuPairingDTO> startPairing(FeishuPairingStartDTO request) {
        return this.pairingUseCase.startPairing(request);
    }

    @Override
    public Response<FeishuPairingDTO> pairingStatus(String pairingId) {
        return this.pairingUseCase.pairingStatus(pairingId);
    }

    @Override
    public Response<Void> cancelPairing(String pairingId) {
        return this.pairingUseCase.cancelPairing(pairingId);
    }

    @Override
    public Response<List<FeishuBotDTO>> listBots() {
        return Response.success(this.botRepository.findAll().stream().map(this::toDTO).toList());
    }

    @Override
    public Response<FeishuBotDTO> saveBot(FeishuBotSaveDTO request) {
        try {
            String botId = this.resolveBotId(request.botId());
            String secretRef = "FEISHU_BOT_" + this.normalize(botId) + "_APP_SECRET";
            if (request.appSecret() != null && !request.appSecret().isBlank()) {
                this.credentialPort.set(CredentialRef.of(secretRef), request.appSecret().trim());
            }
            Optional<FeishuBotAggregate> existing = this.botRepository.findByBotId(botId);
            if (existing.isEmpty() && (request.appId() == null || request.appId().isBlank())) {
                return Response.invalidArgument("新建 Bot 必须提供 appId");
            }
            FeishuBotAggregate bot = existing.orElseGet(() -> new FeishuBotAggregate(botId, request.botName(),
                    request.appId().trim(), secretRef, false, request.agentBaseId(), request.channelCode(), request.cwd(),
                    request.groupRequireMention() == null || request.groupRequireMention(), Instant.now(), Instant.now()));
            bot.rename(request.botName());
            if (request.agentBaseId() != null || request.channelCode() != null || request.cwd() != null) {
                bot.updateConfig(request.agentBaseId(), request.channelCode(), request.cwd());
            }
            if (request.groupRequireMention() != null) {
                bot.setGroupRequireMention(request.groupRequireMention());
            }
            bot.setUpdatedAt(Instant.now());
            this.botRepository.save(bot);
            log.info("[UseCase] 保存飞书 Bot {}（app={}）", botId, bot.getAppId());
            return Response.success(this.toDTO(bot));
        } catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        } catch (Exception e) {
            return Response.internalError("保存 Bot 失败：" + e.getMessage());
        }
    }

    @Override
    public Response<FeishuBotDTO> updateBot(String botId, FeishuBotSaveDTO request) {
        FeishuBotSaveDTO withId = new FeishuBotSaveDTO(botId, request.botName(), request.appId(), request.appSecret(),
                request.agentBaseId(), request.channelCode(), request.cwd(), request.groupRequireMention());
        return this.saveBot(withId);
    }

    @Override
    public Response<Void> deleteBot(String botId) {
        try {
            Optional<FeishuBotAggregate> found = this.botRepository.findByBotId(botId);
            found.ifPresent(bot -> {
                this.connectionPort.stop(botId);
                this.credentialPort.unset(CredentialRef.of(bot.getSecretRef()));
            });
            this.botRepository.deleteByBotId(botId);
            return Response.success(null);
        } catch (Exception e) {
            return Response.internalError("删除 Bot 失败：" + e.getMessage());
        }
    }

    @Override
    public Response<FeishuBotDTO> enableBot(String botId) {
        Optional<FeishuBotAggregate> found = this.botRepository.findByBotId(botId);
        if (found.isEmpty()) {
            return Response.invalidArgument("Bot 不存在: " + botId);
        }
        FeishuBotAggregate bot = found.get();
        if (!this.secretConfigured(bot)) {
            bot.onError("尚未配置 app_secret，请先在编辑中填写");
            return Response.invalidArgument("尚未配置 app_secret，请先在编辑中填写");
        }
        bot.enable();
        this.botRepository.save(bot);
        try {
            this.connectionPort.start(bot);
        } catch (Exception e) {
            bot.onError(e.getMessage());
        }
        return Response.success(this.toDTO(bot));
    }

    @Override
    public Response<FeishuBotDTO> disableBot(String botId) {
        Optional<FeishuBotAggregate> found = this.botRepository.findByBotId(botId);
        if (found.isEmpty()) {
            return Response.invalidArgument("Bot 不存在: " + botId);
        }
        FeishuBotAggregate bot = found.get();
        this.connectionPort.stop(botId);
        bot.disable();
        bot.onStopped();
        this.botRepository.save(bot);
        return Response.success(this.toDTO(bot));
    }

    @Override
    public Response<FeishuBotDTO> testBot(String botId) {
        Optional<FeishuBotAggregate> found = this.botRepository.findByBotId(botId);
        if (found.isEmpty()) {
            return Response.invalidArgument("Bot 不存在: " + botId);
        }
        FeishuBotAggregate bot = found.get();
        if (!this.secretConfigured(bot)) {
            return Response.invalidArgument("尚未配置 app_secret，请先在编辑中填写");
        }
        if (!this.connectionPort.isConnected(botId)) {
            try {
                this.connectionPort.start(bot);
            } catch (Exception e) {
                bot.onError(e.getMessage());
                return Response.success(this.toDTO(bot));
            }
            int attempt = 0;
            while (attempt < 20 && bot.getConnectionState() == FeishuConnectionState.CONNECTING) {
                attempt++;
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return Response.success(this.toDTO(bot));
    }

    /**
     * 解析（补齐/校验）botId。
     *
     * @param botId 入参 botId
     * @return 规范化 botId
     */
    private String resolveBotId(String botId) {
        if (botId == null || botId.isBlank()) {
            return "fbot-" + Long.toHexString(System.currentTimeMillis() & 0xFFFFL);
        }
        String normalized = botId.trim().toLowerCase();
        if (!BOT_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "botId 只允许小写字母、数字与连字符（3~63 位，字母或数字开头）: " + botId);
        }
        return normalized;
    }

    /**
     * 规范化 botId 为环境变量风格（用于 secretRef）。
     *
     * @param botId botId
     * @return 大写、非 [A-Z0-9_] 替换为 _
     */
    private String normalize(String botId) {
        return botId.toUpperCase().replace('-', '_').replaceAll("[^A-Z0-9_]", "_");
    }

    /**
     * 判断 app_secret 是否已配置。
     *
     * @param bot Bot 聚合
     * @return 已配置返回 true
     */
    private boolean secretConfigured(FeishuBotAggregate bot) {
        return this.credentialPort.resolve(CredentialRef.of(bot.getSecretRef()))
                .map(ResolvedCredentialVO::value).map(v -> !v.isBlank()).orElse(false);
    }

    /**
     * 聚合转视图 DTO。
     *
     * @param bot Bot 聚合
     * @return 视图 DTO
     */
    private FeishuBotDTO toDTO(FeishuBotAggregate bot) {
        return new FeishuBotDTO(bot.getBotId(), bot.getBotName(), bot.getAppId(), bot.getSecretRef(),
                this.secretConfigured(bot), bot.isEnabled(), bot.getAgentBaseId(), bot.getChannelCode(), bot.getCwd(),
                bot.isGroupRequireMention(), this.connectionPort.stateOf(bot.getBotId()),
                this.connectionPort.lastErrorOf(bot.getBotId()),
                bot.getUpdatedAt() == null ? null : bot.getUpdatedAt().toString());
    }
}