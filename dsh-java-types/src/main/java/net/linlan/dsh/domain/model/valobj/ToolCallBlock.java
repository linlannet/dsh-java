package net.linlan.dsh.domain.model.valobj;

/** 模型请求的一次工具调用。 */
public record ToolCallBlock(String id, String name, String arguments) implements ContentBlock {}
