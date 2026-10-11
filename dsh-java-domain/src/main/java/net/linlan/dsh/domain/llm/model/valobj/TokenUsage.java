package net.linlan.dsh.domain.llm.model.valobj;

/**
 * TokenUsage 表示TokenUsage。
 */
public record TokenUsage(
        int inputTokens,
        int outputTokens,
        Integer cacheReadTokens,
        Integer cacheWriteTokens,
        Integer reasoningTokens
) {
    /**
     * 构造TokenUsage领域对象。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param inputTokens inputTokensToken列表。
     */
    public TokenUsage(int inputTokens, int outputTokens) {
        this(inputTokens, outputTokens, null, null, null);
    }

    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    public TokenUsage plus(TokenUsage other) {
        if (other == null) {
            return this;
        }
        return new TokenUsage(
                inputTokens + other.inputTokens,
                outputTokens + other.outputTokens,
                addNullable(cacheReadTokens, other.cacheReadTokens),
                addNullable(cacheWriteTokens, other.cacheWriteTokens),
                addNullable(reasoningTokens, other.reasoningTokens)
        );
    }

    private static Integer addNullable(Integer left, Integer right) {
        if (left == null && right == null) {
            return null;
        }
        return (left == null ? 0 : left) + (right == null ? 0 : right);
    }
}
