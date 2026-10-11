package net.linlan.dsh.domain.tool.model.valobj;

/**
 * GuardDecision 是 GUARD 阶段的守卫决策。
 * <p>Allow 表示放行；Deny 表示拒绝（工具主体不执行，返回拒绝结果）。</p>
 */
public sealed interface GuardDecision permits GuardDecision.Allow, GuardDecision.Deny {

    /** 放行。 */
    record Allow() implements GuardDecision {}

    /** 拒绝。 */
    record Deny(String reason, String errorCode) implements GuardDecision {
        public Deny {
            if (reason == null || reason.isEmpty()) {
                throw new IllegalArgumentException("reason must not be empty");
            }
            errorCode = (errorCode == null || errorCode.isEmpty()) ? "GUARD_DENIED" : errorCode;
        }
    }

    /** 创建放行决策。 */
    static GuardDecision allow() {
        return new Allow();
    }

    /** 创建拒绝决策。 */
    static GuardDecision deny(String reason) {
        return new Deny(reason, "GUARD_DENIED");
    }

    /** 创建拒绝决策（带错误码）。 */
    static GuardDecision deny(String reason, String errorCode) {
        return new Deny(reason, errorCode);
    }

    /** 是否为拒绝。 */
    default boolean isDenied() {
        return this instanceof Deny;
    }
}
