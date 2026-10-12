package net.linlan.dsh.domain.agent.service.subagent;

import net.linlan.dsh.domain.agent.service.subagent.adapter.port.ISubagentDriverPort;

import java.util.Objects;

public final class SdkSubagentDriver {

    public record SubagentSpec(String agentPreset, String cwd, String command, String initialMessage) {
        public SubagentSpec {
            Objects.requireNonNull(agentPreset, "agentPreset must not be null");
            Objects.requireNonNull(cwd, "cwd must not be null");
            Objects.requireNonNull(command, "command must not be null");
        }
    }

    private final ISubagentDriverPort port;

    public SdkSubagentDriver(ISubagentDriverPort port) {
        this.port = Objects.requireNonNull(port, "port must not be null");
    }

    public String spawn(SubagentSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        String id = port.spawn(spec.agentPreset(), spec.cwd(), spec.command());
        if (spec.initialMessage() != null && !spec.initialMessage().isEmpty()) {
            port.send(id, spec.initialMessage());
        }
        return id;
    }

    public String send(String subagentId, String message) {
        Objects.requireNonNull(subagentId, "subagentId must not be null");
        Objects.requireNonNull(message, "message must not be null");
        return port.send(subagentId, message);
    }

    public void shutdown(String subagentId) {
        port.shutdown(subagentId);
    }

    public boolean isAlive(String subagentId) {
        return port.isAlive(subagentId);
    }
}
