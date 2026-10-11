package net.linlan.dsh.infrastructure.adapter.skill;

import net.linlan.dsh.domain.skill.model.entity.SkillCandidate;
import net.linlan.dsh.domain.skill.model.valobj.SkillSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemSkillProviderPortTest {

    @TempDir
    Path tempDir;

    @Test
    void discoversConfiguredRootsAndUsesLowerRankForDuplicateNames() throws IOException {
        Path userRoot = tempDir.resolve("user");
        Path customRoot = tempDir.resolve("custom");
        writeSkill(userRoot.resolve("skills"), "shared-skill", "from user");
        writeSkill(customRoot, "shared-skill", "from custom");

        FilesystemSkillProviderPort provider = new FilesystemSkillProviderPort(
                userRoot, null, "test", List.of(customRoot), true, true);

        SkillCandidate winner = provider.discover(tempDir.toString()).stream()
                .filter(candidate -> candidate.name().equals("shared-skill"))
                .min(Comparator.comparingInt(SkillCandidate::rank))
                .orElseThrow();

        assertEquals(SkillSource.CUSTOM, winner.source());
        assertEquals("from custom", winner.description());
    }

    @Test
    void disabledProviderReturnsEmptyCatalog() throws IOException {
        Path root = tempDir.resolve("root");
        writeSkill(root, "disabled-skill", "disabled");

        FilesystemSkillProviderPort provider = new FilesystemSkillProviderPort(
                tempDir, null, "test", List.of(root), false, true);

        assertTrue(provider.discover(tempDir.toString()).isEmpty());
    }

    @Test
    void disabledProjectRootSkipsWorkspaceSkills() throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Path userRoot = tempDir.resolve("user");
        writeSkill(projectRoot.resolve(".dsh").resolve("skills"), "project-skill", "project");

        FilesystemSkillProviderPort provider = new FilesystemSkillProviderPort(
                userRoot, null, "test", List.of(), true, false);

        assertTrue(provider.discover(projectRoot.toString()).stream()
                .noneMatch(candidate -> candidate.name().equals("project-skill")));
    }

    @Test
    void discoversDirectoryBundleSkills() throws IOException {
        Path customRoot = tempDir.resolve("skills");
        Path skillRoot = customRoot.resolve("baidu-ai-search");
        Files.createDirectories(skillRoot);
        Files.writeString(skillRoot.resolve("SKILL.md"), """
                ---
                description: Use Baidu AI Search MCP.
                when_to_use: Current web information is required.
                ---

                Use the search tools.
                """);

        FilesystemSkillProviderPort provider = new FilesystemSkillProviderPort(
                tempDir, null, "test", List.of(customRoot), true, true);

        List<SkillCandidate> candidates = provider.discover(tempDir.toString());
        SkillCandidate candidate = candidates.stream()
                .filter(item -> item.name().equals("baidu-ai-search"))
                .findFirst()
                .orElseThrow();

        assertEquals("Use Baidu AI Search MCP.", candidate.description());
        assertEquals("Current web information is required.", candidate.whenToUse().orElseThrow());
    }

    private void writeSkill(Path root, String name, String description) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve(name + ".md"), "---\ndescription: " + description + "\n---\n\nBody\n");
    }
}
