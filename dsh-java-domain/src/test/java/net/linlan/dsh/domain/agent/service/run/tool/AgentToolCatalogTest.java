package net.linlan.dsh.domain.agent.service.run.tool;

import net.linlan.dsh.domain.skill.ISkillService;
import net.linlan.dsh.domain.skill.model.entity.SkillDefinition;
import net.linlan.dsh.domain.skill.model.entity.SkillSummary;
import net.linlan.dsh.domain.skill.model.valobj.SkillInvocationPolicy;
import net.linlan.dsh.domain.skill.model.valobj.SkillSource;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AgentToolCatalogTest {

    @Test
    void exposesSkillToolWhenSkillServiceIsConfigured() {
        AgentToolCatalog catalog = new AgentToolCatalog(
                null, null, null, null, null, null, null, null, skillService(),
                null, null, null, null, null, null, null, null, null);

        var registry = catalog.createRegistry(
                new AgentToolCatalog.AgentToolContext("agent", "session", null, null, null),
                new InMemoryToolRegistry());

        assertTrue(registry.lookup("skill").isPresent());
        assertTrue(registry.lookup("plugin_status").isPresent());
    }

    private ISkillService skillService() {
        return new ISkillService() {
            @Override
            public List<SkillSummary> list(String cwd) {
                return List.of(new SkillSummary(
                        "example", "Example", Optional.empty(),
                        SkillInvocationPolicy.both(), SkillSource.RUNTIME, "test", Optional.empty()));
            }

            @Override
            public Optional<SkillDefinition> get(String name, String cwd) {
                return Optional.empty();
            }
        };
    }
}
