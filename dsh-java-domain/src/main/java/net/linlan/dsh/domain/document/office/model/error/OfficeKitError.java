package net.linlan.dsh.domain.document.office.model.error;

import net.linlan.dsh.domain.common.error.HarnessError;

/**
 * OfficeKitError 表示 Office Kit 不可用或转换失败。
 *
 * <p>调用流程：Office 工具校验/转换 → Kit 缺失或转换失败 → 抛出本错误。</p>
 * <p>示例：未安装 LibreOffice 时返回 {@code OFFICE_KIT_UNAVAILABLE}，不阻断应用启动。</p>
 */
public class OfficeKitError extends HarnessError {

    /** Office Kit 不可用。 */
    public static final String UNAVAILABLE = "OFFICE_KIT_UNAVAILABLE";
    /** 转换执行失败。 */
    public static final String CONVERSION_FAILED = "OFFICE_CONVERSION_FAILED";
    /** 输入格式不支持。 */
    public static final String UNSUPPORTED_FORMAT = "OFFICE_UNSUPPORTED_FORMAT";
    /** 输入文件不存在。 */
    public static final String INPUT_NOT_FOUND = "OFFICE_INPUT_NOT_FOUND";

    /**
     * 使用错误码、消息与原因创建。
     *
     * @param errorCode 错误码
     * @param message   可读消息
     * @param cause     底层原因
     */
    public OfficeKitError(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * 创建 Kit 缺失错误。
     *
     * @param detail 缺失描述
     * @param cause  底层原因
     * @return Kit 缺失错误
     */
    public static OfficeKitError unavailable(String detail, Throwable cause) {
        return new OfficeKitError(UNAVAILABLE, "office kit unavailable: " + detail, cause);
    }

    /**
     * 创建转换失败错误。
     *
     * @param detail 失败描述
     * @param cause  底层原因
     * @return 转换失败错误
     */
    public static OfficeKitError conversionFailed(String detail, Throwable cause) {
        return new OfficeKitError(CONVERSION_FAILED, "office conversion failed: " + detail, cause);
    }
}
