package net.linlan.dsh.domain.skill.model.valobj;

/**
 * SkillInvocationPolicy 表示SkillInvocationPolicy。
 */
public record SkillInvocationPolicy(boolean modelInvocable, boolean userInvocable) {
    /**
     * 处理both。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public static SkillInvocationPolicy both() { return new SkillInvocationPolicy(true, true); }
}
