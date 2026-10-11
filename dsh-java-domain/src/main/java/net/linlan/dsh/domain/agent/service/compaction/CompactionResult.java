package net.linlan.dsh.domain.agent.service.compaction;

import net.linlan.dsh.domain.model.entity.Message;
import java.util.List;

/**
 * Result of a compaction operation.
 * <p>
 * Mirrors TS {@code CompactionResult}: records the summary message,
 * the replaced event range, and token accounting.
 *
 * <p>调用流程：模型输出 → 工具调用 → 结果回填 → 下一步模型调用；每一步写入会话事件。</p>
 * <p>示例：模型请求执行 fs_read 后，工具结果作为观察值进入下一轮模型输入。</p>
 *
 */
public record CompactionResult(
        String summary,
        long startSeq,
        long endSeq,
        long tokensBefore,
        long tokensAfter,
        List<Message> replacedMessages,
        Message summaryMessage
) {}
