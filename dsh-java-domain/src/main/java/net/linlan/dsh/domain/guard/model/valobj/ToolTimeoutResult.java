package net.linlan.dsh.domain.guard.model.valobj;

/**
 * Result substituted when a tool-call timeout policy wins.
 *
 * @param timeoutMs the elapsed budget
 * @param message   model-facing error message
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record ToolTimeoutResult(long timeoutMs, String message) {

    public static final String CODE = "TOOL_TIMEOUT";

    /**
     * 转换为 ol Timeout 结果。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param timeoutMs 超时时间，毫秒。
     * @return 方法执行结果。
     */
    public ToolTimeoutResult(long timeoutMs) {
        this(timeoutMs, "tool call timed out after " + timeoutMs + "ms");
    }
}
