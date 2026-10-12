package net.linlan.dsh.domain.agent.service.subagent.adapter.port;

public interface ISubagentDriverPort {

    String spawn(String agentPreset, String cwd, String command);

    String send(String subagentId, String message);

    void shutdown(String subagentId);

    boolean isAlive(String subagentId);
}
