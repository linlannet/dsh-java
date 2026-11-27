package net.linlan.dsh.domain.typert.model.valobj;

import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.ImageAttachmentRef;
import net.linlan.dsh.domain.model.valobj.ImageBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.tool.service.ToolResultRenderer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PtcProgramOutput 收集 PTC 程序通过 {@code inspect(...)} 显式选择的输出。
 *
 * <p>调用流程：程序调用 inspect → 按调用顺序记录 → 转成 ContentBlock 列表。</p>
 * <p>示例：{@code inspect("done", image)} 输出文本在前、图片在后；函数返回值与末尾表达式不自动进入结果。</p>
 */
public final class PtcProgramOutput {

    public static final int DEFAULT_MAX_RENDER_BYTES = 64 * 1024 * 1024;
    private static final String TRUNCATION_NOTICE = "[... output truncated; remaining output omitted ...]";

    private final List<ContentBlock> blocks = new ArrayList<>();
    private final Map<Object, Inspection> inspections = new IdentityHashMap<>();
    private final int maxRenderBytes;
    private long renderedBytes;
    private boolean truncated;

    public PtcProgramOutput() {
        this(DEFAULT_MAX_RENDER_BYTES);
    }

    public PtcProgramOutput(int maxRenderBytes) {
        if (maxRenderBytes <= 0) {
            throw new IllegalArgumentException("maxRenderBytes must be positive");
        }
        this.maxRenderBytes = maxRenderBytes;
    }

    /**
     * 返回工具声明的对象或数组，并记录其最终渲染文本供未修改原值的直接 inspect 使用。
     *
     * @param result 成功的工具执行结果
     * @return 传给程序的原对象或数组；其他结果值原样返回
     */
    public Object bindToolResult(ToolExecutionResult.Success result) {
        Objects.requireNonNull(result, "result");
        Object value = result.value();
        if (isInspectable(result)) {
            inspections.put(value, new Inspection(snapshot(value), ToolResultRenderer.render(result)));
        }
        return value;
    }

    /**
     * 按传入顺序记录检查项。字符串直出，图片转 {@link ImageBlock}，其他值使用对象格式。
     * 超出文本展示字节预算时保留 UTF-8 安全前缀并附一次省略提示，不中断调用方执行。
     *
     * @param values 需要展示的值
     */
    public void inspect(Object... values) {
        if (values == null) {
            return;
        }
        for (Object value : values) {
            if (truncated) {
                return;
            }
            if (value instanceof ImageAttachmentRef image) {
                blocks.add(new ImageBlock(image));
            } else if (value instanceof ImageBlock image) {
                blocks.add(image);
            } else if (value instanceof TextBlock text) {
                appendText(text.text());
            } else if (value instanceof ContentBlock block) {
                blocks.add(block);
            } else {
                appendText(render(value));
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

    public boolean isTruncated() {
        return truncated;
    }

    public int maxRenderBytes() {
        return maxRenderBytes;
    }

    private void appendText(String text) {
        int textBytes = text.getBytes(StandardCharsets.UTF_8).length;
        if (renderedBytes + textBytes <= maxRenderBytes) {
            blocks.add(new TextBlock(text));
            renderedBytes += textBytes;
            return;
        }

        int available = (int) Math.max(0, maxRenderBytes - renderedBytes);
        String prefix = utf8Prefix(text, available);
        if (!prefix.isEmpty()) {
            blocks.add(new TextBlock(prefix));
        }
        renderedBytes = maxRenderBytes;
        truncated = true;
        blocks.add(new TextBlock(TRUNCATION_NOTICE));
    }

    private String render(Object value) {
        if (value != null) {
            Inspection inspection = inspections.get(value);
            if (inspection != null && matchesSnapshot(value, inspection.snapshot())) {
                return inspection.text();
            }
        }
        if (value instanceof ToolExecutionResult.Success result && isInspectable(result)) {
            return ToolResultRenderer.render(result);
        }
        if (value instanceof String text) {
            return text;
        }
        return String.valueOf(value);
    }

    private static boolean isInspectable(ToolExecutionResult.Success result) {
        return (result.value() instanceof Map<?, ?> || result.value() instanceof List<?>)
                && result.content().stream().allMatch(TextBlock.class::isInstance);
    }

    private static Object snapshot(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(key, snapshot(item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(snapshot(item)));
            return copy;
        }
        return value;
    }

    private static boolean matchesSnapshot(Object value, Object snapshot) {
        if (value instanceof Map<?, ?> map && snapshot instanceof Map<?, ?> expected) {
            if (map.size() != expected.size()) {
                return false;
            }
            for (Map.Entry<?, ?> entry : expected.entrySet()) {
                if (!map.containsKey(entry.getKey())
                        || !matchesSnapshot(map.get(entry.getKey()), entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if (value instanceof List<?> list && snapshot instanceof List<?> expected) {
            if (list.size() != expected.size()) {
                return false;
            }
            for (int i = 0; i < list.size(); i++) {
                if (!matchesSnapshot(list.get(i), expected.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.deepEquals(value, snapshot);
    }

    private static String utf8Prefix(String text, int maxBytes) {
        int bytes = 0;
        int offset = 0;
        while (offset < text.length()) {
            int codePoint = text.codePointAt(offset);
            int codePointBytes = codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
            if (bytes + codePointBytes > maxBytes) {
                break;
            }
            bytes += codePointBytes;
            offset += Character.charCount(codePoint);
        }
        return text.substring(0, offset);
    }

    private record Inspection(Object snapshot, String text) {}
}
