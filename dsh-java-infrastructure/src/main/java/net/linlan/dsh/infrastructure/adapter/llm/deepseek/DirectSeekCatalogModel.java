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
        Integer maxTokens,
        Integer imageTargetBytes
) {
    public DirectSeekCatalogModel {
        java.util.Objects.requireNonNull(id, "id");
        if (imageTargetBytes != null
                && (imageTargetBytes <= 0 || imageTargetBytes > ImageEncoder.MAX_IMAGE_BYTES)) {
            throw new IllegalArgumentException("imageTargetBytes must be positive and no greater than 32 MiB");
        }
    }

    /** 兼容原五参数目录项。 */
    public DirectSeekCatalogModel(String id, String name, String description,
                                  Integer contextWindow, Integer maxTokens) {
        this(id, name, description, contextWindow, maxTokens, null);
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
    /** 当前模型的编码字节软目标。 */
    public Optional<Integer> imageTargetBytesOpt() { return Optional.ofNullable(imageTargetBytes); }
}
