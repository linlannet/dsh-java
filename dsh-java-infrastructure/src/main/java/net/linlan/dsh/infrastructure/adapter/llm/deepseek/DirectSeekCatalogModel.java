package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import java.util.Optional;

/**
 * DeepSeek 适配器对外暴露的一个可选模型目录项。
 * <p>
 * 对应 TS 侧 {@code DeepSeekCatalogModel}。
 *
 * <p>调用流程：模型请求 → HTTP 客户端 → 解析 JSON/SSE → 归一化为领域事件。</p>
 * <p>示例：上游返回 token delta 时，适配器转换为 LLM 增量事件。</p>
 *
 */
public record DirectSeekCatalogModel(
        String id,
        String name,
        String description,
        Integer contextWindow,
        Integer maxTokens
) {
    public DirectSeekCatalogModel {
        java.util.Objects.requireNonNull(id, "id");
    }

    /**
     * 处理名称 Opt。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */
    public Optional<String> nameOpt() { return Optional.ofNullable(name); }
    /**
     * 处理description Opt。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */
    public Optional<String> descriptionOpt() { return Optional.ofNullable(description); }
    /**
     * 处理上下文 Window Opt。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */
    public Optional<Integer> contextWindowOpt() { return Optional.ofNullable(contextWindow); }
    /**
     * 处理max Tokens Opt。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */
    public Optional<Integer> maxTokensOpt() { return Optional.ofNullable(maxTokens); }
}
