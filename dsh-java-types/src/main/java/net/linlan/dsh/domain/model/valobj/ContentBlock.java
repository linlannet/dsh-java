package net.linlan.dsh.domain.model.valobj;

/**
 * 所有内容块类型的密封接口联合。
 * <p>
 * 对应 TS 侧 {@code ContentBlock}。在 Java 中使用 sealed 层级，保证 switch 分支可以穷尽处理。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public sealed interface ContentBlock permits
        TextBlock, ReasoningBlock, ImageBlock, ToolCallBlock, ToolResultBlock {
}
