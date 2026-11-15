package net.linlan.dsh.domain.terminal.model.valobj;

import net.linlan.dsh.domain.common.error.HarnessError;

/**
 * TerminalError 是终端子系统的可分类错误基类。
 *
 * <p>调用流程：终端会话查找/状态校验/资源清理失败 → 抛出具体子类 → 工具管线记录 name/code。</p>
 * <p>示例：会话不存在返回 {@code TERMINAL_SESSION_NOT_FOUND}。</p>
 */
public class TerminalError extends HarnessError {

    /** 终端会话不存在。 */
    public static final String SESSION_NOT_FOUND = "TERMINAL_SESSION_NOT_FOUND";
    /** 终端会话未运行。 */
    public static final String SESSION_NOT_RUNNING = "TERMINAL_SESSION_NOT_RUNNING";

    /**
     * 使用错误码与消息创建。
     *
     * @param errorCode 错误码
     * @param message   可读消息
     */
    public TerminalError(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 使用错误码、消息与原因创建。
     *
     * @param errorCode 错误码
     * @param message   可读消息
     * @param cause     底层原因
     */
    public TerminalError(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
