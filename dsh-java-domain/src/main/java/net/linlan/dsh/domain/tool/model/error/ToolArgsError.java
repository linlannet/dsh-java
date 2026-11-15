package net.linlan.dsh.domain.tool.model.error;

import net.linlan.dsh.domain.common.error.HarnessError;
import net.linlan.dsh.domain.tool.model.valobj.ToolErrorCodes;

/**
 * ToolArgsError 表示工具参数不合法。
 *
 * <p>调用流程：工具校验参数 → 抛出本错误 → 工具执行管线记录 {@code INVALID_ARGS}。</p>
 * <p>示例：无沙箱执行器时传入 {@code sandbox_permissions}，返回 {@code invalid arguments: sandbox_permissions unsupported}。</p>
 */
public class ToolArgsError extends HarnessError {

    private static final String MESSAGE_PREFIX = "invalid arguments: ";

    /**
     * 使用字段描述创建参数错误。
     *
     * @param detail 具体参数问题
     */
    public ToolArgsError(String detail) {
        super(ToolErrorCodes.INVALID_ARGS, MESSAGE_PREFIX + detail);
    }

    /**
     * 使用字段描述与原因创建参数错误。
     *
     * @param detail 具体参数问题
     * @param cause  底层原因
     */
    public ToolArgsError(String detail, Throwable cause) {
        super(ToolErrorCodes.INVALID_ARGS, MESSAGE_PREFIX + detail, cause);
    }
}
