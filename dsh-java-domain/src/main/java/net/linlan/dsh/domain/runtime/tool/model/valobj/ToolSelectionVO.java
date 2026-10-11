package net.linlan.dsh.domain.runtime.tool.model.valobj;

import java.util.List;

/**
 * Holds the requested tool codes for one task submission.
 *
 * @param toolCodes requested tool codes
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record ToolSelectionVO(List<String> toolCodes) {

    public ToolSelectionVO {
        toolCodes = toolCodes == null ? List.of() : List.copyOf(toolCodes);
    }
}
