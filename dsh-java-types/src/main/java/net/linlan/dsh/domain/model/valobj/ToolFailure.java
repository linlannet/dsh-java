package net.linlan.dsh.domain.model.valobj;

import java.util.Map;

/**
 * 工具执行失败的结构化信息。
 * 对应 TS 侧 {@code ToolErrorInfo} 和 {@code ToolFailure}。
 *
 * @param message 人类可读的错误消息
 * @param info    结构化错误信息，包含名称和错误码
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record ToolFailure(String message, ToolErrorInfo info) {

    public ToolFailure {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must be non-blank");
        }
    }

    /**
     * 创建工具失败对象。
     * @param message 错误消息。
     * @param code 错误码。
     */
    public ToolFailure(String message, String code) {
        this(message, new ToolErrorInfo(code, code));
    }

/** 结构化错误标识，对应 TS 侧 {@code ToolErrorInfo}。 */
    public record ToolErrorInfo(String name, String code) {
        public ToolErrorInfo {
            if (name == null || name.isBlank()) {
                name = "TOOL_ERROR";
            }
            if (code == null || code.isBlank()) {
                code = "TOOL_ERROR";
            }
        }
    }
}
