package net.linlan.dsh.domain.agent.team.service;

import net.linlan.dsh.domain.agent.team.adapter.port.IAgentTeamRepository;
import net.linlan.dsh.domain.agent.team.model.entity.SharedTask;
import net.linlan.dsh.domain.agent.team.model.entity.TeamMessage;
import net.linlan.dsh.domain.agent.team.model.valobj.AgentTeamRoster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentTeamService 多代理团队")
class AgentTeamServiceTest {

    private static class InMemoryRepo implements IAgentTeamRepository {
        final Map<String, AgentTeamRoster> teams = new HashMap<>();
        final Map<String, SharedTask> tasks = new HashMap<>();

        @Override
        public void save(AgentTeamRoster roster) { teams.put(roster.teamId(), roster); }

        @Override
        public Optional<AgentTeamRoster> findById(String teamId) { return Optional.ofNullable(teams.get(teamId)); }

        @Override
        public void saveMessage(TeamMessage message) { }

        @Override
        public void saveTask(SharedTask task) { tasks.put(task.taskId(), task); }

        @Override
        public List<SharedTask> findTasks(String teamId) {
            return tasks.values().stream().filter(t -> t.teamId().equals(teamId)).toList();
        }
    }

    @Test
    @DisplayName("createTeam 创建团队并持久化")
    void createTeam() {
        var repo = new InMemoryRepo();
        var service = new AgentTeamService(repo);

        var roster = service.createTeam("leader-1", List.of("mate-1", "mate-2"));
        assertNotNull(roster.teamId());
        assertTrue(repo.findById(roster.teamId()).isPresent());
    }

    @Test
    @DisplayName("sendMessage 投递消息到 teammate inbox")
    void sendMessage() {
        var repo = new InMemoryRepo();
        var service = new AgentTeamService(repo);

        var roster = service.createTeam("leader-1", List.of("mate-1"));
        var msg = service.sendMessage(roster.teamId(), "leader-1", "mate-1", "hello");
        assertEquals("hello", msg.content());
    }

    @Test
    @DisplayName("sendMessage teammate 不存在抛异常")
    void sendMessageTeammateNotFound() {
        var repo = new InMemoryRepo();
        var service = new AgentTeamService(repo);

        var roster = service.createTeam("leader-1", List.of("mate-1"));
        assertThrows(AgentTeamService.TeammateNotFoundException.class,
                () -> service.sendMessage(roster.teamId(), "leader-1", "nonexistent", "hello"));
    }

    @Test
    @DisplayName("createTask 创建共享任务")
    void createTask() {
        var repo = new InMemoryRepo();
        var service = new AgentTeamService(repo);

        var roster = service.createTeam("leader-1", List.of("mate-1"));
        var task = service.createTask(roster.teamId(), "实现功能X", "mate-1");
        assertEquals("open", task.status());
        assertEquals("实现功能X", task.title());
    }

    @Test
    @DisplayName("restore 冷恢复已存储团队")
    void restore() {
        var repo = new InMemoryRepo();
        var service = new AgentTeamService(repo);

        var roster = service.createTeam("leader-1", List.of("mate-1"));
        var restored = service.restore(roster.teamId());
        assertTrue(restored.isPresent());
        assertEquals("leader-1", restored.get().leader());
    }
}
