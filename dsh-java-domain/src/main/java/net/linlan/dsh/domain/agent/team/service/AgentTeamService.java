package net.linlan.dsh.domain.agent.team.service;

import net.linlan.dsh.domain.agent.team.adapter.port.IAgentTeamRepository;
import net.linlan.dsh.domain.agent.team.model.entity.SharedTask;
import net.linlan.dsh.domain.agent.team.model.entity.TeamMessage;
import net.linlan.dsh.domain.agent.team.model.valobj.AgentTeamRoster;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * AgentTeamService 提供 Lead 与具名 teammate 的 roster 管理、直接 inbox 消息投递、
 * 持久共享任务板（创建/更新任务）、冷恢复已存储 teammate。
 *
 * <p>实验性扩展，默认关闭，需在 app 模块显式配置 @ConditionalOnProperty(name="dsh.experimental.agent-team.enabled", havingValue="true") 启用。</p>
 *
 */
public class AgentTeamService {

    private final IAgentTeamRepository repository;

    public AgentTeamService(IAgentTeamRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    public AgentTeamRoster createTeam(String lead, List<String> teammates) {
        String teamId = UUID.randomUUID().toString();
        var roster = new AgentTeamRoster(teamId, lead, teammates, "direct");
        repository.save(roster);
        return roster;
    }

    public TeamMessage sendMessage(String teamId, String fromAgent, String toAgent, String content) {
        var roster = repository.findById(teamId);
        if (roster.isEmpty()) {
            throw new TeamNotFoundException("团队不存在: " + teamId);
        }
        if (!roster.get().teammates().contains(toAgent) && !roster.get().lead().equals(toAgent)) {
            throw new TeammateNotFoundException("teammate 不存在: " + toAgent);
        }
        var message = new TeamMessage(teamId, fromAgent, toAgent, content, Instant.now());
        repository.saveMessage(message);
        return message;
    }

    public SharedTask createTask(String teamId, String title, String assignee) {
        var task = new SharedTask(UUID.randomUUID().toString(), teamId, title, "open", assignee, Instant.now());
        repository.saveTask(task);
        return task;
    }

    public List<SharedTask> listTasks(String teamId) {
        return repository.findTasks(teamId);
    }

    public Optional<AgentTeamRoster> restore(String teamId) {
        return repository.findById(teamId);
    }

    public static class TeamNotFoundException extends RuntimeException {
        public TeamNotFoundException(String message) { super(message); }
    }

    public static class TeammateNotFoundException extends RuntimeException {
        public TeammateNotFoundException(String message) { super(message); }
    }
}
