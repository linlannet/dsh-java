package net.linlan.dsh.domain.agent.model.valobj;

/**
 * InboxTarget 表示收件箱写入目标的领域值对象。
 */
public enum InboxTarget {
    NEXT_TURN("next-turn"),
    NEXT_STEP("next-step");

    private final String wireName;

    InboxTarget(String wireName) {
        this.wireName = wireName;
    }

    /**
     * 处理wire 名称。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public String wireName() {
        return wireName;
    }

    /**
     * 根据 Wire 名称。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param name 名称。
     * @return 创建的领域对象。
     */
    public static InboxTarget fromWireName(String name) {
        for (InboxTarget t : values()) {
            if (t.wireName.equals(name)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown inbox target: " + name);
    }
}
