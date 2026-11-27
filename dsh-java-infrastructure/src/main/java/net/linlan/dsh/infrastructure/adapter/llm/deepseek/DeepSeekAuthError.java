package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import net.linlan.dsh.domain.common.error.HarnessError;

/** DeepSeek 认证配置与凭据错误。 */
public class DeepSeekAuthError extends HarnessError {

    public static final String AUTH_MISSING = "DEEPSEEK_AUTH_MISSING";

    public DeepSeekAuthError(String message) {
        super(AUTH_MISSING, message);
    }

    public static DeepSeekAuthError missingProvider() {
        return new DeepSeekAuthError(
                "DeepSeekAuthProvider is not configured. Register a DeepSeekAuthProvider bean, "
                        + "or configure harness.llm.deepseek.api-key / DEEPSEEK_API_KEY."
        );
    }

    public static DeepSeekAuthError missingCredential() {
        return new DeepSeekAuthError(
                "DeepSeek credential is missing. Set harness.llm.deepseek.api-key / DEEPSEEK_API_KEY, "
                        + "or provide apiKey in the active model setting."
        );
    }
}
