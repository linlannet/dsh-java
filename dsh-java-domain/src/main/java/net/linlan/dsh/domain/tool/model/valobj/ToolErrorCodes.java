package net.linlan.dsh.domain.tool.model.valobj;

/**
 * ToolErrorCodes 表示ToolErrorCodesCode列表。
 */
public final class ToolErrorCodes {
    public static final String ABORTED = "ABORTED";
    public static final String ABORTED_BEFORE_DISPATCH = "ABORTED_BEFORE_DISPATCH";
    public static final String UNKNOWN_TOOL = "UNKNOWN_TOOL";
    public static final String INVALID_ARGS = "INVALID_ARGS";
    public static final String INVALID_TOOL_OUTPUT = "INVALID_TOOL_OUTPUT";
    public static final String TOOL_TIMEOUT = "TOOL_TIMEOUT";

    /**
     * 转换为 ol Error Codes。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    private ToolErrorCodes() {}
}
