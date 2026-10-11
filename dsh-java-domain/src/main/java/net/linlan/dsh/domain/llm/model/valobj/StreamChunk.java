package net.linlan.dsh.domain.llm.model.valobj;

import net.linlan.dsh.domain.model.valobj.ContentBlock;

/**
 * StreamChunk 表示StreamChunk。
 */
public sealed interface StreamChunk permits
        StreamChunk.BlockStart,
        StreamChunk.TextDelta,
        StreamChunk.ReasoningDelta,
        StreamChunk.ToolCallDelta,
        StreamChunk.BlockEnd,
        StreamChunk.Usage,
        StreamChunk.Finish,
        StreamChunk.Retry {

    /**
     * BlockStart 表示BlockStart。
     */
    record BlockStart(int index, String blockType) implements StreamChunk {}

    /**
     * TextDelta 表示TextDelta。
     */
    record TextDelta(int index, String text) implements StreamChunk {}

    /**
     * ReasoningDelta 表示ReasoningDelta。
     */
    record ReasoningDelta(int index, String text) implements StreamChunk {}

    /**
     * ToolCallDelta 表示ToolCallDelta。
     */
    record ToolCallDelta(int index, String id, String name, String argumentsDelta) implements StreamChunk {}

    /**
     * BlockEnd 表示BlockEnd。
     */
    record BlockEnd(int index, ContentBlock block) implements StreamChunk {}

    /**
     * Usage 表示Usage。
     */
    record Usage(TokenUsage usage) implements StreamChunk {}

    /**
     * Finish 表示Finish。
     */
    record Finish(FinishReason reason) implements StreamChunk {}

    /**
     * Retry 表示Retry。
     */
    record Retry(int attempt, Integer maxAttempts, String failureMessage) implements StreamChunk {}

    /**
     * 判断TokenDelta是否成立。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param chunk chunk。
     * @return 领域处理结果。
     */
    static boolean isTokenDelta(StreamChunk chunk) {
        if (chunk instanceof TextDelta td) return !td.text().isEmpty();
        if (chunk instanceof ReasoningDelta rd) return !rd.text().isEmpty();
        if (chunk instanceof ToolCallDelta tcd) return !tcd.argumentsDelta().isEmpty() || tcd.name() != null;
        return false;
    }
}
