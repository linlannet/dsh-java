package net.linlan.dsh.domain.tool.web.browser;

import net.linlan.dsh.domain.common.error.HarnessError;

/**
 * BrowserUseError 表示浏览器操作执行失败。
 *
 * <p>调用流程：浏览器工具调用操作端口 → 端口异常或空结果 → 抛出本错误。</p>
 * <p>示例：截图端口返回 null 时返回 {@code BROWSER_EMPTY_RESULT}。</p>
 */
public class BrowserUseError extends HarnessError {

    /** 操作执行失败。 */
    public static final String EXECUTION_FAILED = "BROWSER_EXECUTION_FAILED";
    /** 操作返回空结果。 */
    public static final String EMPTY_RESULT = "BROWSER_EMPTY_RESULT";

    /**
     * 使用错误码、消息与原因创建。
     *
     * @param errorCode 错误码
     * @param message   可读消息
     * @param cause     底层原因
     */
    public BrowserUseError(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
