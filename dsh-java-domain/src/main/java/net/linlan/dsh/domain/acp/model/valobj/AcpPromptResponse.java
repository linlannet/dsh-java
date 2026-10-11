package net.linlan.dsh.domain.acp.model.valobj;

import java.util.List;

/**
 * AcpPromptResponse 表示AcpPromptResponse提示词响应。
 */
public record AcpPromptResponse(String sessionId, String stopReason, List<AcpMessage> messages) {}
