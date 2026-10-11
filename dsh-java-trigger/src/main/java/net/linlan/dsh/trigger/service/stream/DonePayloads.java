package net.linlan.dsh.trigger.service.stream;

import java.util.List;
import java.util.Map;

public final class DonePayloads {
    private DonePayloads() {
    }

    public static List<Map<String, Object>> currentTurnMessages(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int lastUserIndex = -1;
        for (int i = messages.size() - 1; i >= 0; --i) {
            if (!"user".equals(String.valueOf(messages.get(i).get("role")))) continue;
            lastUserIndex = i;
            break;
        }
        if (lastUserIndex <= 0) {
            return messages;
        }
        return messages.subList(lastUserIndex, messages.size());
    }

    public static int messageCount(List<Map<String, Object>> messages) {
        return messages == null ? 0 : messages.size();
    }
}

