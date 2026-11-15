package net.linlan.dsh.domain.tool.web.model.error;

import net.linlan.dsh.domain.common.error.HarnessError;

/**
 * WebBillingError 表示 Web 计费模式与凭据不匹配。
 *
 * <p>调用流程：凭据解析失败 → 抛出本错误 → 工具结果记录 code，并保留 cause。</p>
 * <p>示例：account 模式但未配置账户凭据时返回 {@code WEB_ACCOUNT_UNAVAILABLE}。</p>
 */
public class WebBillingError extends HarnessError {

    /** 缺少任意可用凭据。 */
    public static final String CREDENTIAL_MISSING = "WEB_CREDENTIAL_MISSING";
    /** account 模式缺少账户凭据。 */
    public static final String ACCOUNT_UNAVAILABLE = "WEB_ACCOUNT_UNAVAILABLE";

    /**
     * 使用错误码、消息与原因创建。
     *
     * @param errorCode 错误码
     * @param message   可读消息
     * @param cause     底层原因
     */
    public WebBillingError(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * 创建缺少凭据错误。
     *
     * @param mode  计费模式
     * @param cause 底层原因
     * @return 凭据错误
     */
    public static WebBillingError credentialMissing(String mode, Throwable cause) {
        return new WebBillingError(CREDENTIAL_MISSING,
                "no usable web credential for billing mode: " + mode, cause);
    }

    /**
     * 创建账户凭据缺失错误。
     *
     * @param cause 底层原因
     * @return 账户凭据错误
     */
    public static WebBillingError accountUnavailable(Throwable cause) {
        return new WebBillingError(ACCOUNT_UNAVAILABLE,
                "account billing requested but no account credential is configured", cause);
    }
}
