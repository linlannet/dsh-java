package net.linlan.dsh.domain.session.search.service;

import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.valobj.AssistantMessagePayload;
import net.linlan.dsh.domain.session.event.model.valobj.ToolCallPayload;
import net.linlan.dsh.domain.session.event.model.valobj.ToolResultPayload;
import net.linlan.dsh.domain.session.event.model.valobj.UserMessagePayload;
import net.linlan.dsh.domain.session.search.adapter.port.ISessionSearchIndexPort;

/**
 * SessionFullTextIndex 是会话全文索引领域服务。
 * <p>接收 SessionEvent，提取文本内容后委托给 {@link ISessionSearchIndexPort} 进行索引。
 */
public final class SessionFullTextIndex {

    private final ISessionSearchIndexPort port;

    public SessionFullTextIndex(ISessionSearchIndexPort port) {
        if (port == null) {
            throw new IllegalArgumentException("port must not be null");
        }
        this.port = port;
    }

    /**
     * 索引一个会话事件。
     *
     * @param sessionId 会话 ID
     * @param event     会话事件
     */
    public void index(String sessionId, SessionEvent event) {
        String content = extractText(event);
        if (content != null && !content.isBlank()) {
            port.index(sessionId, event.seq(), content);
        }
    }

    /**
     * 从事件中提取可索引的文本内容。
     */
    private String extractText(SessionEvent event) {
        if (event instanceof SessionEvent.UserMessage um) {
            return extractFromMessage(um.data());
        }
        if (event instanceof SessionEvent.AssistantMessage am) {
            return extractFromAssistantMessage(am.data());
        }
        if (event instanceof SessionEvent.ToolCall tc) {
            return extractFromToolCall(tc.data());
        }
        if (event instanceof SessionEvent.ToolResult tr) {
            return extractFromToolResult(tr.data());
        }
        return null;
    }

    private String extractFromMessage(UserMessagePayload data) {
        if (data == null || data.message() == null) {
            return null;
        }
        return extractTextBlocks(data.message().content());
    }

    private String extractFromAssistantMessage(AssistantMessagePayload data) {
        if (data == null || data.message() == null) {
            return null;
        }
        return extractTextBlocks(data.message().content());
    }

    private String extractFromToolCall(ToolCallPayload data) {
        if (data == null) {
            return null;
        }
        return data.name() + " " + data.arguments();
    }

    private String extractFromToolResult(ToolResultPayload data) {
        if (data == null || data.message() == null) {
            return null;
        }
        return extractTextBlocks(data.message().content());
    }

    private String extractTextBlocks(java.util.List<? extends net.linlan.dsh.domain.model.valobj.ContentBlock> content) {
        StringBuilder sb = new StringBuilder();
        for (var block : content) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.text()).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
