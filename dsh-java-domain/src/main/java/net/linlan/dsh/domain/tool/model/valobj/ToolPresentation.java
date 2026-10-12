package net.linlan.dsh.domain.tool.model.valobj;

/**
 * ToolPresentation 表示工具结果的三种呈现形式。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public enum ToolPresentation {

    /** PTC 形式：结构化工具调用结果。 */
    PTC,

    /** NATIVE 形式：原生文本输出。 */
    NATIVE,

    /** SELECTOR 形式：选择器交互结果。 */
    SELECTOR;

    /**
     * 处理from Name。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param name name参数。
     * @return 方法执行结果。
     */
    public static ToolPresentation fromName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("presentation name must not be blank");
        }
        String normalized = name.trim().toUpperCase();
        return switch (normalized) {
            case "PTC" -> PTC;
            case "NATIVE" -> NATIVE;
            case "SELECTOR" -> SELECTOR;
            default -> throw new IllegalArgumentException("unknown presentation: " + name);
        };
    }
}
