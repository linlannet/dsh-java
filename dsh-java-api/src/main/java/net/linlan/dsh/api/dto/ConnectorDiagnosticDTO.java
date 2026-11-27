package net.linlan.dsh.api.dto;

/** 前端可安全展示的连接器分类诊断。 */
public record ConnectorDiagnosticDTO(String code, String message, boolean retryable) {
}
