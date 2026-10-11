package net.linlan.dsh.infrastructure.adapter.acp;

import net.linlan.dsh.domain.acp.adapter.port.IAcpAgentPort;
import net.linlan.dsh.domain.acp.model.valobj.AcpPromptRequest;
import net.linlan.dsh.domain.acp.model.valobj.AcpPromptResponse;
import net.linlan.dsh.domain.acp.model.valobj.AcpSessionInfo;

/** Stub ACP agent port used when no ACP command is configured. */
public class StubAcpAgentPort implements IAcpAgentPort {
    @Override public AcpSessionInfo initialize(String cwd, String mcpConfig) {
        throw new IllegalStateException("ACP command not configured");
    }
    @Override public AcpPromptResponse prompt(AcpPromptRequest request) {
        throw new IllegalStateException("ACP command not configured");
    }
    @Override public void cancel(String sessionId) {}
    @Override public void dispose(String sessionId) {}
}
