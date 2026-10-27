package net.linlan.dsh.domain.agent.team.model.valobj;

import java.util.List;
import java.util.Objects;

/**
 * AgentTeamRoster 表示多代理团队花名册值对象。
 *
 */
public record AgentTeamRoster(
        String teamId,
        String leader,
        List<String> teammates,
        String messageStrategy
) {
    public AgentTeamRoster {
        Objects.requireNonNull(teamId, "teamId 不能为空");
        Objects.requireNonNull(leader, "leader 不能为空");
        teammates = teammates == null ? List.of() : List.copyOf(teammates);
        messageStrategy = messageStrategy == null ? "direct" : messageStrategy;
    }
}
