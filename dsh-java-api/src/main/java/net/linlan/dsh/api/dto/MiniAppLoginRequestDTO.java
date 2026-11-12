package net.linlan.dsh.api.dto;

/**
 * 小程序登录请求。
 *
 * @param code 微信 wx.login 返回的临时 code
 */
public record MiniAppLoginRequestDTO(String code) {
}