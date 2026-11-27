package net.linlan.dsh.domain.connector;

import net.linlan.dsh.domain.common.error.HarnessError;

/** 连接器提供方抛出的安全分类错误，消息不得包含凭据。 */
public class ConnectorAccountException extends HarnessError {
    private final boolean retryable;

    public ConnectorAccountException(String errorCode, String message, boolean retryable) {
        super(errorCode, message);
        this.retryable = retryable;
    }

    public ConnectorAccountException(String errorCode, String message, boolean retryable, Throwable cause) {
        super(errorCode, message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
