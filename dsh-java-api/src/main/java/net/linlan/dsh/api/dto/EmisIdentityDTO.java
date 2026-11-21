package net.linlan.dsh.api.dto;

import java.util.List;

/**
 * linlan-emis 企业身份（OIDC 登录后由宿主返回给前端；不含 token）。
 *
 * @param userId   平台用户 ID（sub）
 * @param tenantId 租户 ID
 * @param roleLevel 角色层级（0/1/2）
 * @param userName 用户名
 * @param scopes   授权范围
 * @param issuer   签发者
 * @param expiresAt access token 过期时间（epoch 秒）
 */
public record EmisIdentityDTO(Long userId, Long tenantId, Integer roleLevel, String userName,
                              List<String> scopes, String issuer, Long expiresAt) {
}