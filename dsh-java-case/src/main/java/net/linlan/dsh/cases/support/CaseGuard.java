package net.linlan.dsh.cases.support;

/**
 * Common case-level guards.
 */
public final class CaseGuard {

    private CaseGuard() {
    }

    /**
     * 校验入参约束，不满足时抛出受控的用例层异常。
     */
    public static String requireSessionId(String sessionId) {
        return requireText(sessionId, "sessionId must not be blank");
    }

    /**
     * 校验入参约束，不满足时抛出受控的用例层异常。
     */
    public static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * 校验入参约束，不满足时抛出受控的用例层异常。
     */
    public static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
