package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.MiniAppLoginRequestDTO;
import net.linlan.dsh.api.dto.MiniAppLoginResponseDTO;
import net.linlan.dsh.api.response.Response;

/**
 * 微信小程序登录用例接口。
 */
public interface IMiniAppApi {

    /**
     * 用微信临时 code 换取短期会话令牌。
     *
     * @param request 登录请求
     * @return 会话令牌
     */
    Response<MiniAppLoginResponseDTO> login(MiniAppLoginRequestDTO request);

    /**
     * 校验小程序会话令牌是否有效。
     *
     * @param token 会话令牌
     * @return true 表示有效
     */
    Response<Boolean> validateSession(String token);
}