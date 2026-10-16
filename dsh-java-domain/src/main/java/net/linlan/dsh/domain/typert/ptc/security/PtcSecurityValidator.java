package net.linlan.dsh.domain.typert.ptc.security;

import java.util.Map;

/**
 * PtcSecurityValidator 校验 PTC 脚本与环境变量是否符合安全策略。
 */
public final class PtcSecurityValidator {

    private final PtcSecurityPolicy policy;

    public PtcSecurityValidator() {
        this(PtcSecurityPolicy.defaults());
    }

    public PtcSecurityValidator(PtcSecurityPolicy policy) {
        this.policy = policy == null ? PtcSecurityPolicy.defaults() : policy;
    }

    public ValidationResult validateScript(String script) {
        if (script == null || script.isBlank()) {
            return ValidationResult.fail("script is empty");
        }
        if (script.length() > policy.maxScriptLength()) {
            return ValidationResult.fail("script exceeds max length " + policy.maxScriptLength());
        }
        for (String pattern : policy.blockedScriptPatterns()) {
            if (script.contains(pattern)) {
                return ValidationResult.fail("script contains blocked pattern: " + pattern);
            }
        }
        return ValidationResult.ok();
    }

    public ValidationResult validateEnv(Map<String, Object> env) {
        if (env == null || env.isEmpty()) {
            return ValidationResult.ok();
        }
        for (String key : env.keySet()) {
            String upperKey = key.toUpperCase();
            for (String blocked : policy.blockedEnvVars()) {
                if (upperKey.contains(blocked)) {
                    return ValidationResult.fail("env var '" + key + "' matches blocked pattern: " + blocked);
                }
            }
        }
        return ValidationResult.ok();
    }

    public ValidationResult validate(String script, Map<String, Object> env) {
        ValidationResult scriptResult = validateScript(script);
        if (!scriptResult.valid()) {
            return scriptResult;
        }
        return validateEnv(env);
    }

    public PtcSecurityPolicy policy() {
        return policy;
    }

    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }
        public static ValidationResult fail(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
