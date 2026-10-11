package net.linlan.dsh.domain.guard.model.valobj;

import java.util.Optional;

/**
 * GuardDecision 表示GuardDecision。
 */
public record GuardDecision(Kind kind, String feedback, Optional<String> additionalContext) {

    /**
     * Kind 定义当前模块中的enum契约与职责。
     */
    public enum Kind { ALLOW, BLOCK }

    /**
     * 列出全部 ow。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 查询到的数据。
     */
    public static GuardDecision allow() {
        return new GuardDecision(Kind.ALLOW, null, Optional.empty());
    }

    /**
     * 列出全部 ow With Reminder。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param reminder reminder参数。
     * @return 查询到的数据。
     */
    public static GuardDecision allowWithReminder(String reminder) {
        return new GuardDecision(Kind.ALLOW, null, Optional.of(reminder));
    }

    /**
     * 处理block。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param feedback feedback参数。
     * @return 方法执行结果。
     */
    public static GuardDecision block(String feedback) {
        return new GuardDecision(Kind.BLOCK, feedback, Optional.empty());
    }

    /**
     * 处理block With Reminder。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param feedback feedback参数。
     * @param reminder reminder参数。
     * @return 方法执行结果。
     */
    public static GuardDecision blockWithReminder(String feedback, String reminder) {
        return new GuardDecision(Kind.BLOCK, feedback, Optional.of(reminder));
    }
}
