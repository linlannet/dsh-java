package net.linlan.dsh.domain.typert.model.valobj;

import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.ImageAttachmentRef;
import net.linlan.dsh.domain.model.valobj.ImageBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * PtcProgramOutput 收集 PTC 程序通过 {@code inspect(...)} 显式选择的输出。
 *
 * <p>调用流程：程序调用 inspect → 按调用顺序记录 → 转成 ContentBlock 列表。</p>
 * <p>示例：{@code inspect("done", image)} 输出文本在前、图片在后；函数返回值与末尾表达式不自动进入结果。</p>
 */
public final class PtcProgramOutput {

    private final List<ContentBlock> blocks = new ArrayList<>();

    /**
     * 按传入顺序记录检查项。字符串直出，图片转 {@link ImageBlock}，其他值使用对象格式。
     *
     * @param values 需要展示的值
     */
    public void inspect(Object... values) {
        if (values == null) {
            return;
        }
        for (Object value : values) {
            if (value instanceof ImageAttachmentRef image) {
                blocks.add(new ImageBlock(image));
            } else if (value instanceof ImageBlock image) {
                blocks.add(image);
            } else if (value instanceof ContentBlock block) {
                blocks.add(block);
            } else {
                blocks.add(new TextBlock(render(value)));
            }
        }
    }

    /**
     * 按记录顺序返回输出内容。
     *
     * @return 不可变内容块列表
     */
    public List<ContentBlock> blocks() {
        return List.copyOf(blocks);
    }

    /**
     * 是否没有任何 inspect 输出。
     *
     * @return true 表示无输出
     */
    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    private String render(Object value) {
        if (value instanceof String text) {
            return text;
        }
        return String.valueOf(value);
    }
}
