package net.linlan.dsh.domain.agent.team.adapter.port;

import net.linlan.dsh.domain.agent.team.model.entity.SharedTask;
import net.linlan.dsh.domain.agent.team.model.entity.TeamMessage;
import net.linlan.dsh.domain.agent.team.model.valobj.AgentTeamRoster;
import java.util.List;
import java.util.Optional;

/**
 * IAgentTeamRepository 多代理团队出站端口。
 *
 */
public interface IAgentTeamRepository {

    void save(AgentTeamRoster roster);

    Optional<AgentTeamRoster> findById(String teamId);

    void saveMessage(TeamMessage message);

    void saveTask(SharedTask task);

    List<SharedTask> findTasks(String teamId);
}
