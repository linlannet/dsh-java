package net.linlan.dsh.domain.agent.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * AgentDefaultModelProvider 表示Agent预设的默认模型提供方。
 *
 * <p>未显式指定模型时使用该 provider 配置的默认模型。</p>
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public class AgentDefaultModelProvider {

    private final Map<String, String> agentPresetToModel = new HashMap<>();
    private final String defaultModel;

    /**
     * 处理Agent Default Model Provider。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param defaultModel default Model参数。
     * @return 方法执行结果。
     */
    public AgentDefaultModelProvider(String defaultModel) {
        this.defaultModel = Objects.requireNonNull(defaultModel, "defaultModel must not be null");
    }

    /**
     * 处理register。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param agentPreset agent Preset参数。
     * @param modelId model Id参数。
     * @return 方法执行结果。
     */
    public void register(String agentPreset, String modelId) {
        if (agentPreset == null || agentPreset.isBlank()) {
            throw new IllegalArgumentException("agentPreset must not be blank");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        agentPresetToModel.put(agentPreset, modelId);
    }

    /**
     * 处理getDefault Model。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param agentPreset agent Preset参数。
     * @return 方法执行结果。
     */
    public String getDefaultModel(String agentPreset) {
        if (agentPreset == null || agentPreset.isBlank()) {
            return defaultModel;
        }
        return agentPresetToModel.getOrDefault(agentPreset, defaultModel);
    }

    /**
     * 返回default Model。
     * @return 方法执行结果。
     */
    public String getDefaultModel() { return defaultModel; }
}
