package net.linlan.dsh.domain.tool.service;

import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;

import java.util.Objects;

/** 将工具最终内容块投影为程序 inspect 可复用的文本。 */
public final class ToolResultRenderer {

    private ToolResultRenderer() {
    }

    /**
     * 按内容块顺序拼接文本，不重新执行工具或输出策略。
     *
     * @param result 已完成的工具执行结果
     * @return 文本块以换行连接后的展示文本
     */
    public static String render(ToolExecutionResult result) {
        Objects.requireNonNull(result, "result");
        StringBuilder text = new StringBuilder();
        boolean first = true;
        for (ContentBlock block : result.content()) {
            if (block instanceof TextBlock textBlock) {
                if (!first) {
                    text.append('\n');
                }
                text.append(textBlock.text());
                first = false;
            }
        }
        return text.toString();
    }
}
