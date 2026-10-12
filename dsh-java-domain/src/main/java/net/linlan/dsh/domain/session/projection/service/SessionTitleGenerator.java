package net.linlan.dsh.domain.session.projection.service;

import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;

import java.util.List;

public final class SessionTitleGenerator {

    public String generate(SessionLog session) {
        List<SessionEvent> events = session.events();
        for (SessionEvent e : events) {
            if (e instanceof SessionEvent.UserMessage um) {
                String text = extractText(um);
                if (text != null && !text.isBlank()) {
                    return truncate(text.trim(), 60);
                }
            }
        }
        return "New Session " + session.sessionId();
    }

    public String update(String oldTitle, SessionLog session) {
        String generated = generate(session);
        if (generated.startsWith("New Session ")) {
            return oldTitle;
        }
        return generated;
    }

    private String extractText(SessionEvent.UserMessage um) {
        try {
            if (um.data() == null || um.data().message() == null) {
                return null;
            }
            var content = um.data().message().content();
            StringBuilder sb = new StringBuilder();
            for (var block : content) {
                if (block instanceof TextBlock tb) {
                    sb.append(tb.text());
                }
            }
            return sb.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
