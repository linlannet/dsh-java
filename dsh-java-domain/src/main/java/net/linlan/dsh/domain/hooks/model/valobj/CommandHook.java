package net.linlan.dsh.domain.hooks.model.valobj;

/**
 * CommandHook 表示命令CommandHook。
 */
public record CommandHook(String command, Integer timeoutSec) {}
