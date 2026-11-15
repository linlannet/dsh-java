package net.linlan.dsh.domain.common.error;

import net.linlan.dsh.domain.model.valobj.ToolFailure;

/**
 * HarnessError 是工具与后端可分类错误的统一基类。
 *
 * <p>调用流程：子类提供稳定错误码 → 工具执行管线捕获 → 写入 tool/result 的 name/code。</p>
 * <p>示例：Web 抓取失败抛出 {@code WebFetchError}，结果记录 {@code name=WebFetchError, code=WEB_FETCH_DNS_FAILED}。</p>
 */
public class HarnessError extends RuntimeException {

    private static final String FALLBACK_CODE = "TOOL_ERROR";

    private final String errorCode;
    private final String errorName;

    /**
     * 使用错误码与消息创建。
     *
     * @param errorCode 稳定错误码
     * @param message   可读消息
     */
    public HarnessError(String errorCode, String message) {
        this(errorCode, null, message, null);
    }

    /**
     * 使用错误码、消息与原因创建。
     *
     * @param errorCode 稳定错误码
     * @param message   可读消息
     * @param cause     底层原因
     */
    public HarnessError(String errorCode, String message, Throwable cause) {
        this(errorCode, null, message, cause);
    }

    /**
     * 使用错误码、错误名、消息与原因创建。
     *
     * @param errorCode 稳定错误码，为空时回落 {@code TOOL_ERROR}
     * @param errorName 错误名，为空时使用具体错误类名
     * @param message   可读消息，为空时使用错误码
     * @param cause     底层原因
     */
    public HarnessError(String errorCode, String errorName, String message, Throwable cause) {
        super(message == null || message.isBlank() ? normalizeCode(errorCode) : message, cause);
        this.errorCode = normalizeCode(errorCode);
        this.errorName = errorName == null || errorName.isBlank()
                ? getClass().getSimpleName()
                : errorName;
    }

    /**
     * 规范化错误码，空值回落为通用错误码。
     *
     * @param errorCode 原始错误码
     * @return 规范化后的错误码
     */
    private static String normalizeCode(String errorCode) {
        return errorCode == null || errorCode.isBlank() ? FALLBACK_CODE : errorCode;
    }

    /**
     * 返回稳定错误码。
     *
     * @return 错误码
     */
    public String errorCode() {
        return errorCode;
    }

    /**
     * 返回错误名。
     *
     * @return 错误名
     */
    public String errorName() {
        return errorName;
    }

    /**
     * 转换为工具失败信息，供工具执行管线统一记录。
     *
     * @return 工具失败信息
     */
    public ToolFailure toFailure() {
        return new ToolFailure(getMessage(), new ToolFailure.ToolErrorInfo(errorName, errorCode));
    }
}
