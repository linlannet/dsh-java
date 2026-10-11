package net.linlan.dsh.domain.model.entity;

import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.valobj.ToolFailure;
import java.util.List;
import java.util.Map;

/**
 * 单次工具调用在本机执行后的判别结果。
 * 对应 TS 侧 {@code ToolExecutionResult}。
 *
 * <p>使用 {@link #isError()} 区分成功与失败。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public sealed interface ToolExecutionResult
        permits ToolExecutionResult.Success, ToolExecutionResult.Failure {

/** 是否执行失败。 */
    boolean isError();

/** 发送给模型的内容块。 */
    List<ContentBlock> content();

/** 供下一次请求使用的额外上下文消息。 */
    List<Message> additionalContexts();

/** 当前结果是否结束当前 Agent 回合。 */
    boolean concludesTurn();

/** 标准化的工具执行成功结果。 */
    record Success(
            Object value,
            List<ContentBlock> content,
            Map<String, Object> meta,
            List<Message> additionalContexts,
            boolean concludesTurn
    ) implements ToolExecutionResult {
        public Success {
            content = content == null ? List.of() : List.copyOf(content);
            additionalContexts = additionalContexts == null ? List.of() : List.copyOf(additionalContexts);
            meta = meta == null ? null : Map.copyOf(meta);
        }
        /** 成功结果不作为失败返回。 */
        @Override public boolean isError() { return false; }

        /** 判断该结果是否结束当前回合。 */
        @Override public boolean concludesTurn() { return concludesTurn; }
    }

/** 标准化的工具执行失败结果。 */
    record Failure(
            ToolFailure error,
            List<ContentBlock> content,
            Map<String, Object> meta,
            List<Message> additionalContexts
    ) implements ToolExecutionResult {
        public Failure {
            content = content == null ? List.of() : List.copyOf(content);
            additionalContexts = additionalContexts == null ? List.of() : List.copyOf(additionalContexts);
            meta = meta == null ? null : Map.copyOf(meta);
        }
        /** 失败结果始终作为失败返回。 */
        @Override public boolean isError() { return true; }

        /** 工具失败通常需要回填给模型并结束当前回合。 */
        @Override public boolean concludesTurn() { return false; }
    }

/** 快速创建简单文本成功结果。 */
    static Success ok(List<ContentBlock> content) {
        return new Success(null, content, null, List.of(), false);
    }

/** 快速创建简单文本失败结果。 */
    static Failure fail(String message, String code) {
        var error = new ToolFailure(message,
                new ToolFailure.ToolErrorInfo(code, code));
        var content = List.of((ContentBlock) new TextBlock(message));
        return new Failure(error, content, null, List.of());
    }
}
