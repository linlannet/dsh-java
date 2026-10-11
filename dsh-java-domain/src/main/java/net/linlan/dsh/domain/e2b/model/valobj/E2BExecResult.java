package net.linlan.dsh.domain.e2b.model.valobj;

/**
 * E2BExecResult 表示E2BExecResult结果。
 */
public record E2BExecResult(String output, String error, int exitCode, long durationMs) {}
