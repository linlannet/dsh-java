package net.linlan.dsh.domain.tool.web.computer;

import net.linlan.dsh.domain.common.error.HarnessError;

/**
 * ComputerUseError 表示桌面操作（CUA）执行失败。
 *
 * <p>调用流程：CUA 工具调用操作端口 → 端口异常或空结果 → 抛出本错误。</p>
 * <p>示例：截图端口抛异常时返回 {@code CUA_EXECUTION_FAILED}。</p>
 */
public class ComputerUseError extends HarnessError {

    /** 操作执行失败。 */
    public static final String EXECUTION_FAILED = "CUA_EXECUTION_FAILED";
    /** 操作返回空结果。 */
    public static final String EMPTY_RESULT = "CUA_EMPTY_RESULT";

    /**
     * 使用错误码、消息与原因创建。
     *
     * @param errorCode 错误码
     * @param message   可读消息
     * @param cause     底层原因
     */
    public ComputerUseError(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
