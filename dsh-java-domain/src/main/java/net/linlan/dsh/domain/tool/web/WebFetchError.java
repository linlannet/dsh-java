package net.linlan.dsh.domain.tool.web;

import net.linlan.dsh.domain.common.error.HarnessError;
import net.linlan.dsh.domain.tool.web.model.valobj.WebFetchErrorCode;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CancellationException;

/**
 * WebFetchError 表示本地网页抓取的传输失败。
 *
 * <p>调用流程：抓取适配器捕获异常 → {@link #from(Throwable)} 归类 → 工具结果记录分类码。</p>
 * <p>示例：连接被拒绝返回 {@code WEB_FETCH_CONNECTION_REFUSED}。</p>
 */
public class WebFetchError extends HarnessError {

    /**
     * 使用分类码、细节与原因创建。
     *
     * @param code   分类码
     * @param detail 底层错误描述
     * @param cause  底层原因
     */
    public WebFetchError(WebFetchErrorCode code, String detail, Throwable cause) {
        super(code.name(), detail, cause);
    }

    /**
     * 将底层异常归类为网页抓取错误。
     *
     * @param error 底层异常
     * @return 分类后的抓取错误
     */
    public static WebFetchError from(Throwable error) {
        Throwable cause = error == null ? new IOException("unknown fetch error") : error;
        WebFetchErrorCode code = classify(cause);
        String systemCode = cause.getClass().getSimpleName();
        String detail = cause.getMessage();
        String message = code.name() + " / " + systemCode
                + (detail == null || detail.isBlank() ? "" : ": " + detail);
        return new WebFetchError(code, message, cause);
    }

    /**
     * 按异常类型归类传输错误。
     *
     * @param cause 底层异常
     * @return 分类码
     */
    private static WebFetchErrorCode classify(Throwable cause) {
        if (cause instanceof CancellationException) {
            return WebFetchErrorCode.WEB_ABORTED;
        }
        if (cause instanceof UnknownHostException) {
            return WebFetchErrorCode.WEB_FETCH_DNS_FAILED;
        }
        if (cause instanceof ConnectException) {
            return WebFetchErrorCode.WEB_FETCH_CONNECTION_REFUSED;
        }
        if (cause instanceof HttpTimeoutException) {
            return WebFetchErrorCode.WEB_FETCH_CONNECTION_TIMEOUT;
        }
        if (cause instanceof SSLException) {
            return WebFetchErrorCode.WEB_FETCH_CERTIFICATE_INVALID;
        }
        if (cause instanceof InterruptedException) {
            return WebFetchErrorCode.WEB_FETCH_CONNECTION_INTERRUPTED;
        }
        if (cause instanceof SocketException) {
            return isUnreachable(cause)
                    ? WebFetchErrorCode.WEB_FETCH_NETWORK_UNREACHABLE
                    : WebFetchErrorCode.WEB_FETCH_CONNECTION_INTERRUPTED;
        }
        return WebFetchErrorCode.WEB_FETCH_NETWORK_ERROR;
    }

    /**
     * 判断是否为网络不可达。
     *
     * @param cause 底层异常
     * @return true 表示不可达
     */
    private static boolean isUnreachable(Throwable cause) {
        String message = cause.getMessage();
        return message != null && message.toLowerCase().contains("unreachable");
    }
}
