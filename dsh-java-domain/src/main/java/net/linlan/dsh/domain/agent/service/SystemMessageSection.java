package net.linlan.dsh.domain.agent.service;

/** 系统消息与运行时上下文共用的章节模型。 */
public record SystemMessageSection(String name, String text) {

    public SystemMessageSection {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("section name required");
        }
        text = text == null ? "" : text;
    }
}
