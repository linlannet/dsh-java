package net.linlan.dsh.domain.skill.office;

import net.linlan.dsh.domain.skill.model.entity.SkillCandidate;
import net.linlan.dsh.domain.skill.model.entity.SkillDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OfficeSkillTest {

    @Test
    void shouldOpenDocument() {
        OfficeSkill skill = new OfficeSkill();
        String result = skill.open("/tmp/doc.xlsx");

        assertNotNull(result);
        assertTrue(skill.getContent("/tmp/doc.xlsx").isPresent());
    }

    @Test
    void shouldRejectNullPathOnOpen() {
        OfficeSkill skill = new OfficeSkill();
        assertThrows(IllegalArgumentException.class, () -> skill.open(null));
    }

    @Test
    void shouldRejectBlankPathOnOpen() {
        OfficeSkill skill = new OfficeSkill();
        assertThrows(IllegalArgumentException.class, () -> skill.open("  "));
    }

    @Test
    void shouldEditDocument() {
        OfficeSkill skill = new OfficeSkill();
        skill.open("/tmp/edit.docx");

        String result = skill.edit("/tmp/edit.docx", Map.of("field", "value"));

        assertNotNull(result);
        assertTrue(result.contains("field=value"));
    }

    @Test
    void shouldEditWithoutExplicitOpen() {
        OfficeSkill skill = new OfficeSkill();
        String result = skill.edit("/tmp/auto.docx", Map.of("k", "v"));
        assertTrue(result.contains("k=v"));
    }

    @Test
    void shouldRejectNullChangesOnEdit() {
        OfficeSkill skill = new OfficeSkill();
        assertThrows(IllegalArgumentException.class, () -> skill.edit("/tmp/x.docx", null));
    }

    @Test
    void shouldRejectNullPathOnEdit() {
        OfficeSkill skill = new OfficeSkill();
        assertThrows(IllegalArgumentException.class, () -> skill.edit(null, Map.of()));
    }

    @Test
    void shouldConvertDocument() {
        OfficeSkill skill = new OfficeSkill();
        skill.open("/tmp/conv.docx");

        String result = skill.convert("/tmp/conv.docx", "pdf");

        assertNotNull(result);
        assertTrue(result.contains("format:pdf"));
    }

    @Test
    void shouldRejectNullFormatOnConvert() {
        OfficeSkill skill = new OfficeSkill();
        assertThrows(IllegalArgumentException.class, () -> skill.convert("/tmp/x.docx", null));
    }

    @Test
    void shouldRejectBlankFormatOnConvert() {
        OfficeSkill skill = new OfficeSkill();
        assertThrows(IllegalArgumentException.class, () -> skill.convert("/tmp/x.docx", "  "));
    }

    @Test
    void shouldLogOperations() {
        OfficeSkill skill = new OfficeSkill();
        skill.open("/tmp/log.docx");
        skill.edit("/tmp/log.docx", Map.of("a", "b"));
        skill.convert("/tmp/log.docx", "pdf");

        List<String> log = skill.operationsLog();
        assertEquals(3, log.size());
        assertTrue(log.get(0).startsWith("open:"));
        assertTrue(log.get(1).startsWith("edit:"));
        assertTrue(log.get(2).startsWith("convert:"));
    }

    @Test
    void shouldDiscoverSkillCandidate() {
        OfficeSkill skill = new OfficeSkill();
        List<SkillCandidate> candidates = skill.discover("/tmp");

        assertEquals(1, candidates.size());
        assertEquals("office", candidates.get(0).name());
    }

    @Test
    void shouldLoadSkillDefinition() {
        OfficeSkill skill = new OfficeSkill();
        List<SkillCandidate> candidates = skill.discover("/tmp");

        Optional<SkillDefinition> def = skill.load(candidates.get(0));
        assertTrue(def.isPresent());
        assertEquals("office", def.get().name());
    }

    @Test
    void shouldReturnEmptyForUnknownCandidate() {
        OfficeSkill skill = new OfficeSkill();
        SkillCandidate unknown = new SkillCandidate(
                "unknown", "desc", Optional.empty(),
                net.linlan.dsh.domain.skill.model.valobj.SkillInvocationPolicy.both(),
                net.linlan.dsh.domain.skill.model.valobj.SkillSource.BUNDLED,
                "x", Optional.empty(), 0, "loc"
        );

        assertTrue(skill.load(unknown).isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullCandidate() {
        OfficeSkill skill = new OfficeSkill();
        assertTrue(skill.load(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyContentForNullPath() {
        OfficeSkill skill = new OfficeSkill();
        assertTrue(skill.getContent(null).isEmpty());
    }
}
