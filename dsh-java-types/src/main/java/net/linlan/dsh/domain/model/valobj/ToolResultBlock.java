package net.linlan.dsh.domain.model.valobj;

import java.util.List;

/** 工具调用结果，会回传给模型。 */
public record ToolResultBlock(
        String toolCallId,
        List<ContentBlock> content,
        boolean isError
) implements ContentBlock {
    public ToolResultBlock {
        content = List.copyOf(content);
    }
}
