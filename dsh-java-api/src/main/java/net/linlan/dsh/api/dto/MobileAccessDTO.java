package net.linlan.dsh.api.dto;

/**
 * 移动设备访问开关。
 *
 * @param enabled 是否允许移动设备配对与连接
 */
public record MobileAccessDTO(boolean enabled) {
}