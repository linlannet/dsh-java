package net.linlan.dsh.domain.llm.model.valobj;

import java.util.List;
import java.util.Objects;

/**
 * LlmCallConfig 表示LlmCallConfig。
 */
public record LlmCallConfig(
        String provider,
        String model,
        String reasoningEffort,
        Double temperature,
        Integer maxTokens,
        List<String> stop
) {
    public LlmCallConfig {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        stop = stop == null ? null : List.copyOf(stop);
    }

    /**
     * 处理configEqualsEqual列表。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param other other。
     * @return 领域处理结果。
     */
    public boolean configEquals(LlmCallConfig other) {
        if (other == null) return false;
        if (!provider.equals(other.provider)) return false;
        if (!model.equals(other.model)) return false;
        if (!Objects.equals(reasoningEffort, other.reasoningEffort)) return false;
        if (!Objects.equals(temperature, other.temperature)) return false;
        if (!Objects.equals(maxTokens, other.maxTokens)) return false;
        if (stop == null || other.stop == null) return stop == other.stop;
        if (stop.size() != other.stop.size()) return false;
        for (int i = 0; i < stop.size(); i++) {
            if (!stop.get(i).equals(other.stop.get(i))) return false;
        }
        return true;
    }
}
