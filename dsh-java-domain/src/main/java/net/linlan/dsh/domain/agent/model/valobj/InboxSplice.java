package net.linlan.dsh.domain.agent.model.valobj;

import net.linlan.dsh.domain.model.entity.Message;
import java.util.List;

/**
 * InboxSplice 表示收件箱拼接指令的领域值对象。
 */
public record InboxSplice(
        InboxTarget target,
        int start,
        int removedCount,
        List<Message> inserted,
        String outcome
) {
    public InboxSplice {
        inserted = inserted == null ? List.of() : List.copyOf(inserted);
    }
}
