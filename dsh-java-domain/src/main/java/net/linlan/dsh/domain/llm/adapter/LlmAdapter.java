package net.linlan.dsh.domain.llm.adapter;

import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import java.util.List;
import java.util.concurrent.Flow;

/**
 * 与具体供应商无关的大模型适配器。
 * <p>
 * 执行流程：
 * 1. 基础设施层实现具体供应商适配器；
 * 2. 运行时端口按 provider 选择对应适配器；
 * 3. 调用方通过流式接口消费模型输出。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface LlmAdapter {

    /**
     * 从模型调用中持续输出块数据。
     */
    Flow.Publisher<StreamChunk> stream(GenerateOptions options);

    List<LlmModelInfo> listModels(String provider);

    LlmResolvedModelInfo resolveModel(String provider, String model);

    /**
     * 返回 provider 的展示信息。
     */
    LlmProviderInfo providerInfo(String provider);

    /**
     * 返回当前适配器服务的 provider 标识。
     */
    String providerId();

    /**
     * 返回 provider 自有的重试策略；若不需要则返回 null。
     */
    default ResolvedRetryPolicy retryPolicy(String provider) {
        return null;
    }

    // ── 嵌套类型 ──────────────────────────────────────────────

    /** 单个 provider 的展示信息。 */
    record LlmProviderInfo(String id, String name) {}

    /** 单个模型条目。 */
    record LlmModelInfo(
            String provider,
            String id,
            String name,
            String description,
            List<String> inputModalities
    ) {}

    /** 精确路由的模型元数据，包含上下文与推理信息。 */
    record LlmResolvedModelInfo(
            String provider,
            String id,
            String name,
            String description,
            List<String> inputModalities,
            LlmModelContext context,
            Integer defaultMaxTokens,
            LlmModelReasoning reasoning
    ) {}

    /** 模型上下文窗口。 */
    record LlmModelContext(int contextWindow) {}

    /** 模型推理能力描述。 */
    record LlmModelReasoning(
            List<LlmReasoningEffortInfo> efforts,
            String defaultEffort
    ) {}

    /** 单个推理档位。 */
    record LlmReasoningEffortInfo(String id, String name, String description) {}
}
