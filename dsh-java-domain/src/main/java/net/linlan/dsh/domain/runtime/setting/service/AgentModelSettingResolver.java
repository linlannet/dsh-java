package net.linlan.dsh.domain.runtime.setting.service;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.runtime.setting.IModelSettingQueryService;
import net.linlan.dsh.domain.runtime.setting.model.valobj.ModelSettingVO;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 解析 Agent 运行模型渠道。请求渠道优先，其次全局激活渠道，最后使用文件兜底配置。
 */
@Service
public class AgentModelSettingResolver {

    private static final Logger log = LoggerFactory.getLogger(AgentModelSettingResolver.class);
    private static final String DEFAULT_PROVIDER = "custom";
    private static final Integer DEFAULT_MAX_TOKENS = 8192;

    private final IModelSettingQueryService modelSettingQueryService;
    private final String fallbackChannel;
    private final String fallbackModel;
    private final String fallbackBaseUrl;
    private final String fallbackApiKey;

    public AgentModelSettingResolver(
            IModelSettingQueryService modelSettingQueryService,
            @Value("${harness.llm.deepseek.default-channel:${LLM_DEFAULT_CHANNEL:default}}") String fallbackChannel,
            @Value("${harness.llm.deepseek.default-model:${LLM_DEFAULT_MODEL:${DEEPSEEK_DEFAULT_MODEL:Deepseek-V4.0-Pro}}}") String fallbackModel,
            @Value("${harness.llm.deepseek.base-url:${LLM_BASE_URL:${DEEPSEEK_BASE_URL:http://127.0.0.1:28080/v1}}}") String fallbackBaseUrl,
            @Value("${harness.llm.deepseek.api-key:${LLM_API_KEY:${DEEPSEEK_API_KEY:}}}") String fallbackApiKey
    ) {
        this.modelSettingQueryService = modelSettingQueryService;
        this.fallbackChannel = fallbackChannel;
        this.fallbackModel = fallbackModel;
        this.fallbackBaseUrl = fallbackBaseUrl;
        this.fallbackApiKey = fallbackApiKey;
    }

    public AgentOptions resolve(String requestedChannelCode, Integer requestedMaxTokens) {
        ModelSettingVO setting = hasText(requestedChannelCode)
                ? modelSettingQueryService.findByChannelCode(requestedChannelCode)
                        .filter(ModelSettingVO::enabled)
                        .orElse(null)
                : modelSettingQueryService.findActive()
                        .filter(ModelSettingVO::enabled)
                        .orElse(null);

        if (setting == null) {
            if (hasText(requestedChannelCode)) {
                log.warn("请求渠道 '{}' 不存在或未启用，已使用文件兜底 channel={} model={}。",
                        requestedChannelCode, fallbackChannel, fallbackModel);
            } else {
                log.warn("数据库中不存在启用的模型渠道，已使用文件兜底 channel={} model={}。",
                        fallbackChannel, fallbackModel);
            }
            return new AgentOptions(
                    fallbackChannel,
                    DEFAULT_PROVIDER,
                    fallbackModel,
                    requestedMaxTokens != null ? requestedMaxTokens : DEFAULT_MAX_TOKENS,
                    fallbackBaseUrl,
                    fallbackApiKey,
                    "openai"
            );
        }

        if (!hasText(setting.baseUrl()) || !hasText(setting.apiKeyRef())) {
            log.warn("模型渠道 '{}={}' 的 baseUrl 或 apiKey 为空，将依赖适配器兜底配置。",
                    setting.channelCode(), setting.modelCode());
        }
        return new AgentOptions(
                setting.channelCode(),
                setting.providerCode(),
                setting.modelCode(),
                requestedMaxTokens != null ? requestedMaxTokens : DEFAULT_MAX_TOKENS,
                setting.baseUrl(),
                setting.apiKeyRef(),
                setting.protocol()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
