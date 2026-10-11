package net.linlan.dsh.domain.sdk.model.valobj;

/**
 * InitializeParams 表示InitializeParamsParam列表。
 */
public record InitializeParams(String cwd, String provider, String model, Integer maxTokens) {}
