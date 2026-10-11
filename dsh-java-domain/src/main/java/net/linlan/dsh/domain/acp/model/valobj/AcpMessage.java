package net.linlan.dsh.domain.acp.model.valobj;

import java.util.List;

/**
 * One ACP message in a session turn.
 *
 * @param role     message role (user, assistant)
 * @param content  list of content blocks (text, image)
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record AcpMessage(String role, List<AcpContentBlock> content) {

    /**
     * AcpContentBlock 定义当前模块中的interface契约与职责。
     */
    public sealed interface AcpContentBlock permits AcpTextBlock, AcpImageBlock {}

    /**
     * AcpTextBlock 定义当前模块中的record契约与职责。
     */
    public record AcpTextBlock(String text) implements AcpContentBlock {}

    /**
     * AcpImageBlock 定义当前模块中的record契约与职责。
     */
    public record AcpImageBlock(String mediaType, String base64Data) implements AcpContentBlock {}
}
