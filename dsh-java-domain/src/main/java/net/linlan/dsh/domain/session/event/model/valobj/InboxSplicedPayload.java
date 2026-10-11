package net.linlan.dsh.domain.session.event.model.valobj;

import net.linlan.dsh.domain.model.entity.Message;
import java.util.List;

/**
 * InboxSplicedPayload 表示InboxSplicedPayload。
 */
public record InboxSplicedPayload(
        String target,
        int start,
        int removedCount,
        List<Message> inserted,
        String outcome
) {
    public InboxSplicedPayload {
        inserted = inserted == null ? List.of() : List.copyOf(inserted);
    }
}
