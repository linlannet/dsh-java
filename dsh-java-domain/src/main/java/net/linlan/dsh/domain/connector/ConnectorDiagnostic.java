package net.linlan.dsh.domain.connector;

import java.util.Objects;

/** 分类诊断；message 必须是可安全展示的消息，不得携带令牌或第三方原始响应体。 */
public record ConnectorDiagnostic(String code, String message, boolean retryable) {
    public ConnectorDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
