package net.linlan.dsh.domain.model.entity;

import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.ToolResultBlock;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 消息的不可变表示，同时用于投递、持久化历史和模型请求。
 * <p>
 * 对应 TS 侧 {@code Message}。消息 ID 在创建时生成并保持稳定。
 *
 * @param id      稳定的消息 ID
 * @param role    会话角色：{@code system}、{@code user} 或 {@code assistant}
 * @param content 发送给模型的完整内容块
 * @param source  消息来源，表示由谁或什么组件产生
 *
 *
 */
public record Message(
        String id,
        String role,
        List<ContentBlock> content,
        MessageSource source
) {
    public Message {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        content = List.copyOf(content);
        Objects.requireNonNull(source, "source");
    }

/** 创建一条带新 ID 的用户消息。 */
    public static Message createUser(List<ContentBlock> content, MessageSource source) {
        return new Message(UUID.randomUUID().toString(), "user", content, source);
    }

/** 创建一条带新 ID 的助手消息。 */
    public static Message createAssistant(List<ContentBlock> content, MessageSource.ModelMessageSource source) {
        return new Message(UUID.randomUUID().toString(), "assistant", content, source);
    }

/** 创建一条带新 ID 的工具结果消息。 */
    public static Message createToolResult(String callId, List<ContentBlock> content, boolean isError) {
        var source = new MessageSource.ToolMessageSource(callId);
        var block = new ToolResultBlock(
                callId, content, isError);
        return new Message(
                UUID.randomUUID().toString(),
                "tool",
                List.of(block),
                source
        );
    }
}
