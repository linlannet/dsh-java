package net.linlan.dsh.domain.model.valobj;

/** 推理或思考内容，与可见文本相互区分。 */
public record ReasoningBlock(String text) implements ContentBlock {}
