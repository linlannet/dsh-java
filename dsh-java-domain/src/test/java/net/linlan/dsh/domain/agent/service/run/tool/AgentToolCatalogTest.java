package net.linlan.dsh.domain.agent.service.run.tool;

import net.linlan.dsh.domain.skill.ISkillService;
import net.linlan.dsh.domain.storage.deliverable.adapter.port.IGitPort;
import net.linlan.dsh.domain.skill.office.OfficeSkill;
import net.linlan.dsh.domain.skill.service.SkillService;
import net.linlan.dsh.domain.skill.model.entity.SkillDefinition;
import net.linlan.dsh.domain.skill.model.entity.SkillSummary;
import net.linlan.dsh.domain.skill.model.valobj.SkillInvocationPolicy;
import net.linlan.dsh.domain.skill.model.valobj.SkillSource;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.tool.web.computer.adapter.port.IComputerUsePort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AgentToolCatalogTest {

    @Test
    void exposesSkillToolWhenSkillServiceIsConfigured() {
        AgentToolCatalog catalog = new AgentToolCatalog(
                null, null, null, null, null, null, null, null, skillService(),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        var registry = catalog.createRegistry(
                new AgentToolCatalog.AgentToolContext("agent", "session", null, null, null),
                new InMemoryToolRegistry());

        assertTrue(registry.lookup("skill").isPresent());
        assertTrue(registry.lookup("plugin_status").isPresent());
    }

    @Test
    void exposesEnterWorktreeWithoutRemovedDirectoryTools() {
        AgentToolCatalog catalog = new AgentToolCatalog(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                gitPort(), null);

        var registry = catalog.createRegistry(
                new AgentToolCatalog.AgentToolContext("agent", "session", null, null, null),
                new InMemoryToolRegistry());

        assertTrue(registry.lookup("enter_worktree").isPresent());
        assertFalse(registry.lookup("create_worktree").isPresent());
        assertFalse(registry.lookup("working_directory").isPresent());
    }

    @Test
    void officeToolsActivateAfterOfficeSkillLoads() {
        AgentToolCatalog catalog = new AgentToolCatalog(
                null, null, null, null, null, null, null, null,
                new SkillService(List.of(new OfficeSkill())),
                null, null, null, null, null, null, null, null, null,
                (inputPath, outputDir) -> "out/report.pdf", null, null, null, null, null);
        var registry = catalog.createRegistry(
                new AgentToolCatalog.AgentToolContext("agent", "session", null, null, null),
                new InMemoryToolRegistry());

        assertFalse(registry.lookup("office_render").isPresent());
        assertFalse(registry.lookup("propose_action").isPresent());
        registry.lookup("skill").orElseThrow().execute(Map.of("name", "office"), null).join();

        assertTrue(registry.lookup("office_render").isPresent());
        assertTrue(registry.lookup("office_convert").isPresent());
        assertTrue(registry.lookup("office_recalculate").isPresent());
    }

    @Test
    void agentWithoutPresetGetsCuaRunCode() {
        AgentToolCatalog catalog = catalogWithCua();

        var registry = catalog.createRegistry(
                contextWithPreset(null), new InMemoryToolRegistry());

        assertTrue(registry.lookup("cua_run_code").isPresent());
        assertFalse(registry.lookup("cua").isPresent());
    }

    @Test
    void cuaPresetGetsRawTool() {
        AgentToolCatalog catalog = catalogWithCua();

        var registry = catalog.createRegistry(
                contextWithPreset("cua"), new InMemoryToolRegistry());

        assertTrue(registry.lookup("cua").isPresent());
        assertFalse(registry.lookup("cua_run_code").isPresent());
    }

    @Test
    void cuaPresetWithoutRuntimeFailsWithReadableReason() {
        AgentToolCatalog catalog = new AgentToolCatalog(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> catalog.createRegistry(contextWithPreset("cua"), new InMemoryToolRegistry()));

        assertTrue(error.getMessage().contains("CUA program tool is unavailable"));
    }

    private AgentToolCatalog catalogWithCua() {
        return new AgentToolCatalog(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, computerUsePort(), null, null);
    }

    private AgentToolCatalog.AgentToolContext contextWithPreset(String preset) {
        SessionLog session = new SessionLog("session",
                SessionHeader.create("session", Instant.EPOCH, null, preset));
        return new AgentToolCatalog.AgentToolContext("agent", "session", null, session, null);
    }

    private IGitPort gitPort() {
        return new IGitPort() {
            @Override
            public String createWorktree(String repoPath, String branch, String targetPath) {
                return targetPath;
            }

            @Override
            public String currentCommit(String repoPath) {
                return "commit";
            }

            @Override
            public boolean isClean(String repoPath) {
                return true;
            }
        };
    }

    private IComputerUsePort computerUsePort() {
        return new IComputerUsePort() {
            @Override
            public byte[] screenshot() {
                return new byte[]{1};
            }

            @Override
            public void mouseClick(int x, int y) {
            }

            @Override
            public void mouseMove(int x, int y) {
            }

            @Override
            public void keyType(String text) {
            }

            @Override
            public void keyPress(String key) {
            }
        };
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
