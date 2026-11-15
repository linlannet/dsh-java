package net.linlan.dsh.domain.tool.web;

import net.linlan.dsh.domain.tool.web.model.valobj.WebFetchErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WebFetchError 传输错误分类")
class WebFetchErrorTest {

    @Test
    @DisplayName("域名解析失败归类为 DNS")
    void dnsFailure() {
        var error = WebFetchError.from(new UnknownHostException("bad.example"));

        assertEquals(WebFetchErrorCode.WEB_FETCH_DNS_FAILED.name(), error.errorCode());
        assertEquals("WebFetchError", error.errorName());
        assertTrue(error.getMessage().contains("UnknownHostException"));
    }

    @Test
    @DisplayName("连接拒绝归类为 CONNECTION_REFUSED")
    void connectionRefused() {
        var error = WebFetchError.from(new ConnectException("refused"));

        assertEquals(WebFetchErrorCode.WEB_FETCH_CONNECTION_REFUSED.name(), error.errorCode());
    }

    @Test
    @DisplayName("连接超时归类为 CONNECTION_TIMEOUT")
    void connectionTimeout() {
        var error = WebFetchError.from(new HttpConnectTimeoutException("timeout"));

        assertEquals(WebFetchErrorCode.WEB_FETCH_CONNECTION_TIMEOUT.name(), error.errorCode());
    }

    @Test
    @DisplayName("证书异常归类为 CERTIFICATE_INVALID")
    void certificateInvalid() {
        var error = WebFetchError.from(new SSLHandshakeException("bad cert"));

        assertEquals(WebFetchErrorCode.WEB_FETCH_CERTIFICATE_INVALID.name(), error.errorCode());
    }

    @Test
    @DisplayName("网络不可达归类为 NETWORK_UNREACHABLE")
    void networkUnreachable() {
        var error = WebFetchError.from(new SocketException("Network is unreachable"));

        assertEquals(WebFetchErrorCode.WEB_FETCH_NETWORK_UNREACHABLE.name(), error.errorCode());
    }

    @Test
    @DisplayName("中断归类为 CONNECTION_INTERRUPTED")
    void interrupted() {
        var error = WebFetchError.from(new InterruptedException("interrupted"));

        assertEquals(WebFetchErrorCode.WEB_FETCH_CONNECTION_INTERRUPTED.name(), error.errorCode());
    }

    @Test
    @DisplayName("取消异常归类为 WEB_ABORTED")
    void aborted() {
        var error = WebFetchError.from(new CancellationException("cancelled"));

        assertEquals(WebFetchErrorCode.WEB_ABORTED.name(), error.errorCode());
    }

    @Test
    @DisplayName("其他 IO 异常回落为 NETWORK_ERROR 并保留 cause")
    void networkErrorFallback() {
        var cause = new IOException("weird");
        var error = WebFetchError.from(cause);

        assertEquals(WebFetchErrorCode.WEB_FETCH_NETWORK_ERROR.name(), error.errorCode());
        assertSame(cause, error.getCause());
    }
}
