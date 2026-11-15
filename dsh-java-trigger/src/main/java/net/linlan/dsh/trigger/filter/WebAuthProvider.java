package net.linlan.dsh.trigger.filter;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * WebAuthProvider 定义 Token 校验与登录交换能力。
 *
 * <p>调用流程：拦截器 → 提供方校验请求头/Cookie → 返回凭据主体。</p>
 * <p>示例：Cookie {@code dsh_session} 与 Bearer Token 均可认证。</p>
 */
public interface WebAuthProvider {

    /**
     * 认证主体。
     *
     * @param token  凭据
     * @param source 来源：header/cookie
     */
    record WebPrincipal(String token, String source) {
    }

    /**
     * 是否配置了认证密钥。
     *
     * @return true 表示启用认证
     */
    boolean isConfigured();

    /**
     * 校验请求凭据。
     *
     * @param request HTTP 请求
     * @return 认证主体，失败为空
     */
    Optional<WebPrincipal> authenticate(HttpServletRequest request);

    /**
     * 判断请求是否携带设备级 Token（{@code devt_} 前缀）。
     *
     * @param request HTTP 请求
     * @return true 表示携带设备 Token
     */
    boolean presentsDeviceToken(HttpServletRequest request);

    /**
     * 登录交换：校验凭据并返回可写入 Cookie 的会话 Token。
     *
     * @param credential 凭据
     * @return 会话 Token，失败为空
     */
    Optional<String> issueSessionToken(String credential);
}
