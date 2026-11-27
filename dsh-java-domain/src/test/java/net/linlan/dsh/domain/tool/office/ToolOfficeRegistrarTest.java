package net.linlan.dsh.domain.tool.office;

import net.linlan.dsh.domain.agent.service.run.tool.InMemoryToolRegistry;
import net.linlan.dsh.domain.skill.office.OfficeSkill;
import net.linlan.dsh.domain.skill.service.SkillService;
import net.linlan.dsh.domain.tool.skill.SkillTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Office 工具按需激活")
class ToolOfficeRegistrarTest {

    @Test
    @DisplayName("仅在 office 技能成功加载后注册三个工具")
    void activatesOnlyAfterOfficeSkillLoads() {
        OfficeSkill officeSkill = new OfficeSkill();
        SkillService skills = new SkillService(List.of(officeSkill));
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        ToolOfficeRegistrar registrar = new ToolOfficeRegistrar(
                (inputPath, outputDir) -> "out/report.pdf", null);
        registry.register(new SkillTool(skills, "/tmp",
                skill -> registrar.register(registry, skill)));

        assertFalse(registry.lookup("office_render").isPresent());

        registry.lookup("skill").orElseThrow()
                .execute(Map.of("name", "office"), null).join();

        assertTrue(registry.lookup("office_render").isPresent());
        assertTrue(registry.lookup("office_convert").isPresent());
        assertTrue(registry.lookup("office_recalculate").isPresent());

        registry.lookup("skill").orElseThrow()
                .execute(Map.of("name", "office"), null).join();
        assertFalse(registry.lookup("propose_action").isPresent());
    }

    @Test
    @DisplayName("缺少转换端口时不激活")
    void unavailableConverterDoesNotActivate() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        ToolOfficeRegistrar registrar = new ToolOfficeRegistrar(null, null);
        OfficeSkill skill = new OfficeSkill();
        var definition = skill.load(skill.discover("/tmp").get(0)).orElseThrow();

        assertFalse(registrar.register(registry, definition));
        assertTrue(registry.all().isEmpty());
    }
}
