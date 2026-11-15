package net.linlan.dsh.domain.skill.office;

import net.linlan.dsh.domain.skill.model.entity.SkillCandidate;
import net.linlan.dsh.domain.skill.model.entity.SkillDefinition;
import net.linlan.dsh.domain.skill.model.valobj.SkillInvocationPolicy;
import net.linlan.dsh.domain.skill.model.valobj.SkillSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Office 技能")
class OfficeSkillTest {

    @Test
    @DisplayName("发现路由与 docx/pptx/xlsx 三个写作技能")
    void discoversRouterAndWritingSkills() {
        OfficeSkill skill = new OfficeSkill();
        List<SkillCandidate> candidates = skill.discover("/tmp");

        assertEquals(4, candidates.size());
        assertEquals(List.of("office", "office-docx", "office-pptx", "office-xlsx"),
                candidates.stream().map(SkillCandidate::name).toList());
    }

    @Test
    @DisplayName("加载每个技能都有内置内容")
    void loadsBundledContent() {
        OfficeSkill skill = new OfficeSkill();

        for (SkillCandidate candidate : skill.discover("/tmp")) {
            Optional<SkillDefinition> def = skill.load(candidate);
            assertTrue(def.isPresent());
            assertEquals(candidate.name(), def.get().name());
            assertTrue(def.get().content().contains("office_")
                    || def.get().content().contains("python-"));
        }
    }

    @Test
    @DisplayName("未知与空候选返回空")
    void unknownCandidateReturnsEmpty() {
        OfficeSkill skill = new OfficeSkill();
        SkillCandidate unknown = new SkillCandidate(
                "unknown", "desc", Optional.empty(),
                SkillInvocationPolicy.both(), SkillSource.BUNDLED,
                "x", Optional.empty(), 0, "loc"
        );

        assertTrue(skill.load(unknown).isEmpty());
        assertTrue(skill.load(null).isEmpty());
    }
}
