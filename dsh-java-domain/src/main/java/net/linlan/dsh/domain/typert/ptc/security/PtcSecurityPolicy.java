package net.linlan.dsh.domain.typert.ptc.security;

import java.util.Set;

/**
 * PtcSecurityPolicy 定义 PTC 沙箱安全策略。
 */
public record PtcSecurityPolicy(
        int maxScriptLength,
        Set<String> blockedEnvVars,
        Set<String> blockedScriptPatterns
) {
    public PtcSecurityPolicy {
        blockedEnvVars = blockedEnvVars == null ? Set.of() : Set.copyOf(blockedEnvVars);
        blockedScriptPatterns = blockedScriptPatterns == null ? Set.of() : Set.copyOf(blockedScriptPatterns);
    }

    public static PtcSecurityPolicy defaults() {
        return new PtcSecurityPolicy(
                65536,
                Set.of("SECRET", "TOKEN", "PASSWORD", "API_KEY", "PRIVATE_KEY", "CREDENTIALS"),
                Set.of("child_process", "eval(", "Function(", "require('child_process')", "process.exit", "fs.writeFileSync")
        );
    }

    public static PtcSecurityPolicy lenient() {
        return new PtcSecurityPolicy(1048576, Set.of(), Set.of());
    }
}
