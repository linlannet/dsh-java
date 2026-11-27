package net.linlan.dsh.domain.skill.office;

import net.linlan.dsh.domain.skill.model.entity.SkillCandidate;
import net.linlan.dsh.domain.skill.model.entity.SkillDefinition;
import net.linlan.dsh.domain.skill.model.valobj.SkillInvocationPolicy;
import net.linlan.dsh.domain.skill.model.valobj.SkillSource;
import net.linlan.dsh.domain.skill.service.SkillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Office 单技能")
class OfficeSkillTest {

    @Test
    @DisplayName("技能清单仅包含 office")
    void discoversSingleOfficeSkill() {
        OfficeSkill skill = new OfficeSkill();

        List<SkillCandidate> candidates = skill.discover("/tmp");

        assertEquals(1, candidates.size());
        assertEquals(OfficeSkill.NAME, candidates.get(0).name());
    }

    @Test
    @DisplayName("主技能不内联格式指导，格式资源按需读取")
    void loadsFormatResourcesOnDemand() {
        OfficeSkill skill = new OfficeSkill();
        SkillDefinition definition = skill.load(skill.discover("/tmp").get(0)).orElseThrow();

        assertFalse(definition.content().contains("python-docx"));
        assertTrue(skill.loadResource("docx").orElseThrow().contains("python-docx"));
        assertTrue(skill.loadResource("pptx.md").orElseThrow().contains("python-pptx"));
        assertTrue(skill.formatGuide("xlsx").orElseThrow().contains("openpyxl"));
        assertTrue(skill.loadResource("unknown").isEmpty());
    }

    @Test
    @DisplayName("SkillService 可按需读取胜出技能的资源")
    void serviceLoadsWinningSkillResource() {
        SkillService service = new SkillService(List.of(new OfficeSkill()));

        assertTrue(service.getResource("office", "docx", "/tmp")
                .orElseThrow().contains("python-docx"));
        assertTrue(service.getResource("office", "", "/tmp").isEmpty());
    }

    @Test
    @DisplayName("旧写作技能与空候选不再加载")
    void unknownCandidateReturnsEmpty() {
        OfficeSkill skill = new OfficeSkill();
        SkillCandidate legacy = new SkillCandidate(
                "office-docx", "desc", Optional.empty(),
                SkillInvocationPolicy.both(), SkillSource.BUNDLED,
                "x", Optional.empty(), 0, "loc"
        );

        assertTrue(skill.load(legacy).isEmpty());
        assertTrue(skill.load(null).isEmpty());
        assertTrue(skill.loadResource(legacy, "docx").isEmpty());
    }
}
