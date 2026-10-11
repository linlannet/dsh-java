package net.linlan.dsh.domain.llm.service;

import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.valobj.ReasoningBlock;
import net.linlan.dsh.domain.model.valobj.ToolCallBlock;
import net.linlan.dsh.domain.llm.model.valobj.FinishReason;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BlockAssembler 表示BlockAssembler。
 */
public class BlockAssembler {

    /**
     * PartialBlock 定义当前模块中的class契约与职责。
     */
    private static final class PartialBlock {
        String blockType;
        StringBuilder text = new StringBuilder();
        String toolCallId;
        String toolCallName;
        /** 由内容块结束事件设置；设置后冻结当前部分内容。 */
        ContentBlock block;

        PartialBlock(String blockType) {
            this.blockType = blockType;
        }
    }

    private final Map<Integer, PartialBlock> partials = new LinkedHashMap<>();
    private final List<Integer> order = new ArrayList<>();
    private TokenUsage usage;
    private FinishReason finish;

    /**
     * 处理push。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param chunk chunk。
     * @return 领域处理结果。
     */
    public void push(StreamChunk chunk) {
        if (chunk instanceof StreamChunk.BlockStart bs) {
            if (!partials.containsKey(bs.index())) {
                order.add(bs.index());
                partials.put(bs.index(), new PartialBlock(bs.blockType()));
            }
        } else if (chunk instanceof StreamChunk.TextDelta td) {
            PartialBlock partial = ensure(td.index(), "text");
            if (partial.block != null) return; // closed; ignore stragglers
            partial.text.append(td.text());
        } else if (chunk instanceof StreamChunk.ReasoningDelta rd) {
            PartialBlock partial = ensure(rd.index(), "reasoning");
            if (partial.block != null) return;
            partial.text.append(rd.text());
        } else if (chunk instanceof StreamChunk.ToolCallDelta tcd) {
            PartialBlock partial = ensure(tcd.index(), "tool-call");
            if (partial.block != null) return;
            if (tcd.id() != null && !tcd.id().isEmpty()) partial.toolCallId = tcd.id();
            if (tcd.name() != null && !tcd.name().isEmpty()) partial.toolCallName = tcd.name();
            partial.text.append(tcd.argumentsDelta());
        } else if (chunk instanceof StreamChunk.BlockEnd be) {
            PartialBlock partial = ensure(be.index(), be.block() != null ? be.block().getClass().getSimpleName() : "unknown");
            if (partial.block != null) return; // first close wins
            partial.block = be.block();
        } else if (chunk instanceof StreamChunk.Usage u) {
            this.usage = u.usage();
        } else if (chunk instanceof StreamChunk.Finish f) {
            this.finish = f.reason();
        }
    }

    /**
     * 处理ensure。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param index index参数。
     * @param blockType block 类型参数。
     * @return 方法执行结果。
     */
    private PartialBlock ensure(int index, String blockType) {
        PartialBlock partial = partials.get(index);
        if (partial == null) {
            partial = new PartialBlock(blockType);
            partials.put(index, partial);
            order.add(index);
        }
        return partial;
    }

    /**
     * 处理assemble。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param partial partial参数。
     * @param index index参数。
     * @return 方法执行结果。
     */
    private ContentBlock assemble(PartialBlock partial, int index) {
        if (partial.block != null) return partial.block;
        switch (partial.blockType) {
            case "text", "TextBlock" -> {
                return new TextBlock(partial.text.toString());
            }
            case "reasoning", "ReasoningBlock" -> {
                return new ReasoningBlock(partial.text.toString());
            }
            case "tool-call", "ToolCallBlock" -> {
                return new ToolCallBlock(
                        partial.toolCallId != null ? partial.toolCallId : "call-" + index,
                        partial.toolCallName != null ? partial.toolCallName : "",
                        partial.text.toString()
                );
            }
            default -> throw new IllegalStateException(
                    "cannot assemble incomplete block of type \"" + partial.blockType + "\"");
        }
    }

    /**
     * 处理block列表。
     * @return 领域处理结果。
     */
    public List<ContentBlock> blocks() {
        List<ContentBlock> all = new ArrayList<>();
        for (int index : order) {
            PartialBlock partial = partials.get(index);
            if (partial == null) throw new IllegalStateException(
                    "BlockAssembler invariant violated: no partial for index " + index);
            all.add(assemble(partial, index));
        }
        // max-tokens truncation: drop tool-call blocks
        if (finish != null && finish instanceof FinishReason.MaxTokens) {
            List<ContentBlock> kept = new ArrayList<>();
            for (ContentBlock block : all) {
                if (!(block instanceof ToolCallBlock)) {
                    kept.add(block);
                }
            }
            return kept;
        }
        return all;
    }

    /**
     * 处理usage。
     * @return 领域处理结果。
     */
    public TokenUsage usage() {
        return usage;
    }

    /**
     * 处理finish。
     * @return 领域处理结果。
     */
    public FinishReason finish() {
        return finish != null ? finish : new FinishReason.Stop();
    }

    /**
     * 处理消息。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param source 来源。
     * @return 领域处理结果。
     */
    public Message message(MessageSource source) {
        return new Message(
                java.util.UUID.randomUUID().toString(),
                "assistant",
                blocks(),
                source != null ? source : new MessageSource.PluginSource("dsh-llm/assembler")
        );
    }
}
