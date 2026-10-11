package net.linlan.dsh.domain.task.execution.service;

import net.linlan.dsh.domain.task.IHarnessExecutionService;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.runtime.setting.IModelSettingQueryService;
import net.linlan.dsh.domain.runtime.setting.model.valobj.ModelSettingVO;
import net.linlan.dsh.domain.task.execution.adapter.port.IModelExecutionPort;
import net.linlan.dsh.domain.task.execution.model.valobj.HarnessExecutionResultVO;
import net.linlan.dsh.domain.task.execution.model.valobj.ModelExecutionResultVO;
import net.linlan.dsh.domain.session.log.adapter.repository.ISessionEventRepository;
import net.linlan.dsh.domain.session.log.model.entity.SessionEventEntity;
import net.linlan.dsh.domain.session.ISessionLogService;
import net.linlan.dsh.domain.session.registry.adapter.repository.IHarnessSessionRepository;
import net.linlan.dsh.domain.session.registry.model.aggregate.HarnessSessionAggregate;
import net.linlan.dsh.domain.shared.model.valobj.HarnessStatusEnumVO;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Executes stored session prompts against the configured model provider.
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
@Service
public class HarnessExecutionService implements IHarnessExecutionService {

    private final IModelExecutionPort modelExecutionPort;
    private final IHarnessSessionRepository harnessSessionRepository;
    private final ISessionEventRepository sessionEventRepository;
    private final ISessionLogService sessionLogService;
    private final IModelSettingQueryService modelSettingQueryService;
    private final String fallbackChannel;
    private final String fallbackModel;
    private final String fallbackBaseUrl;
    private final String fallbackApiKey;

    /**
     * 初始化任务执行服务。
     * <p>流程：读取队列任务 → 委托模型执行 → 回写执行状态和事件。</p>
     */
    public HarnessExecutionService(
            IModelExecutionPort modelExecutionPort,
            IHarnessSessionRepository harnessSessionRepository,
            ISessionEventRepository sessionEventRepository,
            ISessionLogService sessionLogService,
            IModelSettingQueryService modelSettingQueryService,
            @Value("${harness.llm.deepseek.default-channel:${LLM_DEFAULT_CHANNEL:default}}") String fallbackChannel,
            @Value("${harness.llm.deepseek.default-model:${LLM_DEFAULT_MODEL:${DEEPSEEK_DEFAULT_MODEL:Deepseek-V4.1-Flash}}}") String fallbackModel,
            @Value("${harness.llm.deepseek.base-url:${LLM_BASE_URL:${DEEPSEEK_BASE_URL:http://127.0.0.1:28080/v1}}}") String fallbackBaseUrl,
            @Value("${harness.llm.deepseek.api-key:${LLM_API_KEY:${DEEPSEEK_API_KEY:}}}") String fallbackApiKey
    ) {
        this.modelExecutionPort = modelExecutionPort;
        this.harnessSessionRepository = harnessSessionRepository;
        this.sessionEventRepository = sessionEventRepository;
        this.sessionLogService = sessionLogService;
        this.modelSettingQueryService = modelSettingQueryService;
        this.fallbackChannel = fallbackChannel;
        this.fallbackModel = fallbackModel;
        this.fallbackBaseUrl = fallbackBaseUrl;
        this.fallbackApiKey = fallbackApiKey;
    }
    /**
     * 执行Session。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */


    @Override
    public HarnessExecutionResultVO executeSession(String sessionId) {
        HarnessSessionAggregate session = harnessSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found: " + sessionId));
        if (session.getStatus() == HarnessStatusEnumVO.PENDING_APPROVAL) {
            throw new IllegalStateException("session is pending approval: " + sessionId);
        }

        String prompt = resolveLatestUserPrompt(sessionId);
        session.changeStatus(HarnessStatusEnumVO.RUNNING);
        harnessSessionRepository.save(session);
        sessionLogService.recordExecutionStarted(sessionId, session.getResolvedModel());

        try {
            ModelExecutionResultVO result = modelExecutionPort.execute(buildOptions(session, prompt));
            session.changeStatus(HarnessStatusEnumVO.COMPLETED);
            harnessSessionRepository.save(session);
            sessionLogService.recordExecutionCompleted(sessionId, result.modelCode(), result.content());
            return new HarnessExecutionResultVO(sessionId, session.getStatus(), result.content(), Instant.now());
        } catch (RuntimeException exception) {
            String failureMessage = "模型执行失败：" + extractFailureMessage(exception);
            session.changeStatus(HarnessStatusEnumVO.FAILED);
            harnessSessionRepository.save(session);
            sessionLogService.recordExecutionFailed(sessionId, failureMessage);
            return new HarnessExecutionResultVO(sessionId, session.getStatus(), failureMessage, Instant.now());
        }
    }

    /**
     * 处理resolveLatestUserPrompt提示词。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    private String resolveLatestUserPrompt(String sessionId) {
        return sessionEventRepository.queryBySessionId(sessionId).stream()
                .filter(event -> "user.message".equals(event.eventType()))
                .reduce((previous, current) -> current)
                .map(SessionEventEntity::payload)
                .filter(prompt -> prompt != null && !prompt.isBlank())
                .orElseThrow(() -> new IllegalStateException("no user prompt found for session: " + sessionId));
    }

    private GenerateOptions buildOptions(HarnessSessionAggregate session, String prompt) {
        Optional<ModelSettingVO> setting = findByChannel(session.getResolvedChannelCode());
        if (setting.isPresent()) {
            if (!setting.get().enabled()) {
                throw new IllegalStateException(
                        "model setting is missing or disabled: " + session.getResolvedChannelCode());
            }
            return buildOptions(setting.get(), session, prompt);
        }
        if (hasText(session.getResolvedChannelCode())
                && !fallbackChannel.equals(session.getResolvedChannelCode())) {
            throw new IllegalStateException(
                    "model setting is missing or disabled: " + session.getResolvedChannelCode());
        }
        var message = Message.createUser(
                List.of(new TextBlock(prompt)),
                new MessageSource.UserSource()
        );
        return new GenerateOptions(
                "deepseek",
                fallbackModel,
                null,
                List.of(message),
                null,
                null,
                null,
                null,
                null,
                session.getSessionId(),
                null,
                fallbackBaseUrl,
                fallbackApiKey,
                "openai"
        );
    }

    private GenerateOptions buildOptions(ModelSettingVO setting,
                                         HarnessSessionAggregate session,
                                         String prompt) {
        var message = Message.createUser(
                List.of(new TextBlock(prompt)),
                new MessageSource.UserSource()
        );
        return new GenerateOptions(
                setting.providerCode(),
                setting.modelCode(),
                null,
                List.of(message),
                null,
                null,
                null,
                null,
                null,
                session.getSessionId(),
                null,
                setting.baseUrl(),
                setting.apiKeyRef(),
                setting.protocol()
        );
    }

    private Optional<ModelSettingVO> findByChannel(String channelCode) {
        return hasText(channelCode)
                ? modelSettingQueryService.findByChannelCode(channelCode)
                : modelSettingQueryService.findActive();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 提取 Failure 消息。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param exception 异常对象。
     * @return 方法执行结果。
     */
    private String extractFailureMessage(RuntimeException exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        return message;
    }
}
