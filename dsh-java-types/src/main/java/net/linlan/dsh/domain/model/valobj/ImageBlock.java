package net.linlan.dsh.domain.model.valobj;

/** 可持久化的位图引用（当前仅用于用户内容）。 */
public record ImageBlock(ImageAttachmentRef attachment) implements ContentBlock {}
