package net.linlan.dsh.domain.lsp.model.valobj;

/**
 * LspLocation 表示LspLocation。
 */
public record LspLocation(String uri, int startLine, int startCol, int endLine, int endCol) {}
