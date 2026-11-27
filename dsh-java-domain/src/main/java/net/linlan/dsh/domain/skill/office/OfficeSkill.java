package net.linlan.dsh.domain.skill.office;

import net.linlan.dsh.domain.skill.adapter.port.ISkillProviderPort;
import net.linlan.dsh.domain.skill.model.entity.SkillCandidate;
import net.linlan.dsh.domain.skill.model.entity.SkillDefinition;
import net.linlan.dsh.domain.skill.model.valobj.SkillInvocationPolicy;
import net.linlan.dsh.domain.skill.model.valobj.SkillSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * OfficeSkill 提供单一 Office 技能；各格式写作指导作为内置资源按需读取。
 */
public class OfficeSkill implements ISkillProviderPort {

    public static final String NAME = "office";

    private static final String DESCRIPTION =
            "Create, edit, render, convert and recalculate Office documents.";
    private static final String RESOURCE_ROOT = "/net/linlan/dsh/domain/skill/office/";
    private static final Set<String> FORMAT_RESOURCES = Set.of("docx.md", "pptx.md", "xlsx.md");
    private static final String CONTENT = """
            # Office

            - Preview or export: `office_render(path=...)` writes a PDF under the office-kit outputs.
            - Format conversion: `office_convert(path=..., format=pdf|docx|xlsx|pptx|odt|ods|odp)`.
            - Spreadsheet formulas: `office_recalculate(path=...)` re-saves the workbook as xlsx.
            - Before writing Word, PowerPoint or Excel content, use `skill(name="office", resource="docx|pptx|xlsx")` to read only the matching format guide.
            - Outputs are artifacts; report the returned path instead of pasting binary content.
            """;

    @Override
    public List<SkillCandidate> discover(String cwd) {
        return List.of(new SkillCandidate(
                NAME,
                DESCRIPTION,
                Optional.of("When working with Office documents"),
                SkillInvocationPolicy.both(),
                SkillSource.BUNDLED,
                NAME,
                Optional.empty(),
                0,
                "office-skill"
        ));
    }

    @Override
    public Optional<SkillDefinition> load(SkillCandidate candidate) {
        if (candidate == null || !NAME.equals(candidate.name())) {
            return Optional.empty();
        }
        return Optional.of(new SkillDefinition(
                NAME,
                DESCRIPTION,
                Optional.of("When working with Office documents"),
                SkillInvocationPolicy.both(),
                SkillSource.BUNDLED,
                NAME,
                CONTENT,
                Optional.empty()
        ));
    }

    /**
     * 按格式名或资源名读取写作指导，不在发现技能时加载。
     *
     * @param resourceName docx/pptx/xlsx 或对应的 .md 资源名
     * @return 资源内容；未知或不可读资源为空
     */
    public Optional<String> loadResource(String resourceName) {
        String normalized = resourceName == null ? ""
                : resourceName.trim().toLowerCase(Locale.ROOT);
        if (!normalized.endsWith(".md")) {
            normalized = normalized + ".md";
        }
        if (!FORMAT_RESOURCES.contains(normalized)) {
            return Optional.empty();
        }
        try (InputStream input = OfficeSkill.class.getResourceAsStream(RESOURCE_ROOT + normalized)) {
            if (input == null) {
                return Optional.empty();
            }
            return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> loadResource(SkillCandidate candidate, String resourceName) {
        if (candidate == null || !NAME.equals(candidate.name())) {
            return Optional.empty();
        }
        return loadResource(resourceName);
    }

    public Optional<String> formatGuide(String format) {
        return loadResource(format);
    }
}
