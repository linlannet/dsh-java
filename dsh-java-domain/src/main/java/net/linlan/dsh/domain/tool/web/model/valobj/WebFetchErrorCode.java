package net.linlan.dsh.domain.tool.web.model.valobj;

/**
 * WebFetchErrorCode 定义本地网页抓取的传输类错误码。
 *
 * <p>调用流程：适配器捕获底层异常 → 归类为本枚举 → 工具结果记录对应 code。</p>
 * <p>示例：域名解析失败返回 {@code WEB_FETCH_DNS_FAILED}。</p>
 */
public enum WebFetchErrorCode {
    WEB_FETCH_DNS_FAILED,
    WEB_FETCH_CONNECTION_REFUSED,
    WEB_FETCH_CONNECTION_TIMEOUT,
    WEB_FETCH_CERTIFICATE_INVALID,
    WEB_FETCH_CONNECTION_INTERRUPTED,
    WEB_FETCH_NETWORK_UNREACHABLE,
    WEB_FETCH_NETWORK_ERROR,
    WEB_ABORTED
}
