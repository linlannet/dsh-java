package net.linlan.dsh.domain.llm.waterfall;

import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

/**
 * LlmStreamHandoff 是 LLM 流式请求在 waterfall 链中传递的交接上下文。
 *
 * @param options        当前请求参数（routing 拦截器可修改 provider/model）
 * @param streamSupplier 流式请求供应者（retry 拦截器可包装为重试逻辑）
 * @param prefixChunks   前置注入 chunk（replay 拦截器注入历史 chunk）
 * @param retryCount     当前重试计数
 * @param metadata       附加元数据（trace 上下文、原始请求参数等）
 */
public record LlmStreamHandoff(
        GenerateOptions options,
        Supplier<Flow.Publisher<StreamChunk>> streamSupplier,
        List<StreamChunk> prefixChunks,
        int retryCount,
        Map<String, Object> metadata
) {

    public LlmStreamHandoff {
        prefixChunks = prefixChunks == null ? List.of() : List.copyOf(prefixChunks);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static LlmStreamHandoff initial(
            GenerateOptions options,
            Supplier<Flow.Publisher<StreamChunk>> streamSupplier
    ) {
        return new LlmStreamHandoff(options, streamSupplier, List.of(), 0, Map.of());
    }

    public LlmStreamHandoff withOptions(GenerateOptions newOptions) {
        return new LlmStreamHandoff(newOptions, streamSupplier, prefixChunks, retryCount, metadata);
    }

    public LlmStreamHandoff withStreamSupplier(Supplier<Flow.Publisher<StreamChunk>> newSupplier) {
        return new LlmStreamHandoff(options, newSupplier, prefixChunks, retryCount, metadata);
    }

    public LlmStreamHandoff withPrefixChunks(List<StreamChunk> chunks) {
        return new LlmStreamHandoff(options, streamSupplier, chunks, retryCount, metadata);
    }

    public LlmStreamHandoff withAdditionalPrefixChunks(List<StreamChunk> extra) {
        var combined = new java.util.ArrayList<>(prefixChunks);
        combined.addAll(extra);
        return new LlmStreamHandoff(options, streamSupplier, combined, retryCount, metadata);
    }

    public LlmStreamHandoff withRetryCount(int count) {
        return new LlmStreamHandoff(options, streamSupplier, prefixChunks, count, metadata);
    }

    public LlmStreamHandoff withMetadata(String key, Object value) {
        var map = new java.util.HashMap<>(metadata);
        map.put(key, value);
        return new LlmStreamHandoff(options, streamSupplier, prefixChunks, retryCount, map);
    }

    public LlmStreamHandoff preserveOriginalOptions(GenerateOptions original) {
        return withMetadata("originalOptions", original);
    }

    @SuppressWarnings("unchecked")
    public GenerateOptions originalOptions() {
        Object val = metadata.get("originalOptions");
        return val instanceof GenerateOptions go ? go : options;
    }
}
