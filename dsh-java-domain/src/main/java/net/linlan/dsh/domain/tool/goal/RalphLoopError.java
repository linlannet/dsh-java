package net.linlan.dsh.domain.tool.goal;

import net.linlan.dsh.domain.common.error.HarnessError;

/**
 * RalphLoopError 表示 Ralph 循环的状态冲突。
 *
 * <p>调用流程：启动/推进 Ralph 循环 → 状态不允许 → 抛出本错误。</p>
 * <p>示例：循环已在运行时再次启动返回 {@code RALPH_LOOP_ALREADY_RUNNING}。</p>
 */
public class RalphLoopError extends HarnessError {

    /** 循环已在运行。 */
    public static final String ALREADY_RUNNING = "RALPH_LOOP_ALREADY_RUNNING";
    /** 循环未运行。 */
    public static final String NOT_RUNNING = "RALPH_LOOP_NOT_RUNNING";

    /**
     * 使用错误码与消息创建。
     *
     * @param errorCode 错误码
     * @param message   可读消息
     */
    public RalphLoopError(String errorCode, String message) {
        super(errorCode, message);
    }
}
