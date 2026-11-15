package net.linlan.dsh.domain.skill.office;

import net.linlan.dsh.domain.skill.adapter.port.ISkillProviderPort;
import net.linlan.dsh.domain.skill.model.entity.SkillCandidate;
import net.linlan.dsh.domain.skill.model.entity.SkillDefinition;
import net.linlan.dsh.domain.skill.model.valobj.SkillInvocationPolicy;
import net.linlan.dsh.domain.skill.model.valobj.SkillSource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OfficeSkill 提供 Office 路由与 docx/pptx/xlsx 写作技能。
 *
 * <p>调用流程：发现候选技能 → 按需加载内置内容 → 引导模型使用 Office 工具。</p>
 * <p>示例：加载 {@code office-docx} 获得 python-docx 写作指引。</p>
 */
public class OfficeSkill implements ISkillProviderPort {

    private static final String ROUTER = "office";
    private static final Map<String, String> DESCRIPTIONS = descriptions();
    private static final Map<String, String> CONTENTS = contents();

    @Override
    public List<SkillCandidate> discover(String cwd) {
        return DESCRIPTIONS.entrySet().stream()
                .map(entry -> new SkillCandidate(
                        entry.getKey(),
                        entry.getValue(),
                        Optional.of("When working with Office documents"),
                        SkillInvocationPolicy.both(),
                        SkillSource.BUNDLED,
                        entry.getKey(),
                        Optional.empty(),
                        0,
                        "office-skill"
                ))
                .toList();
    }

    @Override
    public Optional<SkillDefinition> load(SkillCandidate candidate) {
        if (candidate == null || !CONTENTS.containsKey(candidate.name())) {
            return Optional.empty();
        }
        String name = candidate.name();
        SkillDefinition def = new SkillDefinition(
                name,
                DESCRIPTIONS.get(name),
                Optional.of("When working with Office documents"),
                SkillInvocationPolicy.both(),
                SkillSource.BUNDLED,
                name,
                CONTENTS.get(name),
                Optional.empty()
        );
        return Optional.of(def);
    }

    private static Map<String, String> descriptions() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(ROUTER, "Office routing skill: choose render/convert/recalculate or a writing skill.");
        map.put("office-docx", "Create and edit Word documents with python-docx.");
        map.put("office-pptx", "Create and edit PowerPoint decks with python-pptx.");
        map.put("office-xlsx", "Create and edit Excel workbooks with openpyxl, then recalculate.");
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> contents() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(ROUTER, """
                # Office routing

                - Preview or export: `office_render(path=...)` writes a PDF under the office-kit outputs.
                - Format conversion: `office_convert(path=..., format=pdf|docx|xlsx|pptx|odt|ods|odp)`.
                - Spreadsheet formulas: `office_recalculate(path=...)` re-saves the workbook as xlsx.
                - New Word/PowerPoint/Excel content: load `office-docx`, `office-pptx` or `office-xlsx`.
                - Outputs are artifacts; report the returned path instead of pasting binary content.
                """);
        map.put("office-docx", """
                # Word writing (python-docx)

                1. Write a Python script using `python-docx` in the active workspace.
                2. Use `fs_write` for the script and `shell` to run it with the workspace Python environment.
                3. Insert headings, paragraphs, tables and images explicitly; avoid manual XML unless required.
                4. Verify the generated file with `office_render` when a visual check is needed.
                """);
        map.put("office-pptx", """
                # PowerPoint writing (python-pptx)

                1. Write a Python script using `python-pptx` in the active workspace.
                2. Build slides from layouts, set titles/bullets, and add charts or images deliberately.
                3. Keep one message per slide and avoid overflowing text boxes.
                4. Verify the deck with `office_render` when a visual check is needed.
                """);
        map.put("office-xlsx", """
                # Excel writing (openpyxl)

                1. Write a Python script using `openpyxl` in the active workspace.
                2. Prefer formulas over hard-coded results; keep sheets and column widths readable.
                3. Run `office_recalculate` after writing so formula caches are refreshed.
                4. Verify key cells by reading the workbook back before reporting completion.
                """);
        return Collections.unmodifiableMap(map);
    }
}
