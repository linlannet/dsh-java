package net.linlan.dsh.domain.runtime.profile.preset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YamlPresetLoader YAML 预设加载")
class YamlPresetLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("load 解析基本字段")
    void loadBasicFields() {
        var loader = new YamlPresetLoader();
        String yaml = """
                name: coder
                description: A coding persona
                system_prompt: You are a coder
                """;
        var preset = loader.load(yaml);
        assertEquals("coder", preset.name());
        assertEquals("A coding persona", preset.description());
        assertEquals("You are a coder", preset.systemPrompt());
        assertTrue(preset.attributes().isEmpty());
    }

    @Test
    @DisplayName("load 解析额外属性放入 attributes")
    void loadExtraAttributes() {
        var loader = new YamlPresetLoader();
        String yaml = """
                name: reviewer
                description: code reviewer
                system_prompt: review code
                language: java
                level: expert
                """;
        var preset = loader.load(yaml);
        assertEquals("reviewer", preset.name());
        assertEquals("java", preset.attributes().get("language"));
        assertEquals("expert", preset.attributes().get("level"));
    }

    @Test
    @DisplayName("load 处理双引号值")
    void loadDoubleQuotedValue() {
        var loader = new YamlPresetLoader();
        String yaml = "name: \"my persona\"\ndescription: \"hello: world\"";
        var preset = loader.load(yaml);
        assertEquals("my persona", preset.name());
        assertEquals("hello: world", preset.description());
    }

    @Test
    @DisplayName("load 处理单引号值")
    void loadSingleQuotedValue() {
        var loader = new YamlPresetLoader();
        String yaml = "name: 'single'\ndescription: 'val: with colon'";
        var preset = loader.load(yaml);
        assertEquals("single", preset.name());
        assertEquals("val: with colon", preset.description());
    }

    @Test
    @DisplayName("load 跳过注释行")
    void loadSkipsComments() {
        var loader = new YamlPresetLoader();
        String yaml = """
                # this is a comment
                name: test
                description: desc # inline comment
                """;
        var preset = loader.load(yaml);
        assertEquals("test", preset.name());
        assertEquals("desc", preset.description());
    }

    @Test
    @DisplayName("load 跳过列表项")
    void loadSkipsListItems() {
        var loader = new YamlPresetLoader();
        String yaml = """
                name: test
                - item1
                - item2
                description: ok
                """;
        var preset = loader.load(yaml);
        assertEquals("test", preset.name());
        assertEquals("ok", preset.description());
    }

    @Test
    @DisplayName("load 空行被忽略")
    void loadIgnoresBlankLines() {
        var loader = new YamlPresetLoader();
        var preset = loader.load("\n\nname: x\n\n");
        assertEquals("x", preset.name());
    }

    @Test
    @DisplayName("load null 抛出 NPE")
    void loadNullThrows() {
        var loader = new YamlPresetLoader();
        assertThrows(NullPointerException.class, () -> loader.load(null));
    }

    @Test
    @DisplayName("loadFromFile 读取文件并解析")
    void loadFromFileParses() throws Exception {
        var loader = new YamlPresetLoader();
        Path file = tempDir.resolve("preset.yml");
        Files.writeString(file, "name: filetest\ndescription: from file\nsystem_prompt: prompt\n");
        var preset = loader.loadFromFile(file.toString());
        assertEquals("filetest", preset.name());
        assertEquals("from file", preset.description());
        assertEquals("prompt", preset.systemPrompt());
    }

    @Test
    @DisplayName("loadFromFile 不存在文件抛出 IllegalArgumentException")
    void loadFromFileMissing() {
        var loader = new YamlPresetLoader();
        assertThrows(IllegalArgumentException.class,
                () -> loader.loadFromFile("/nonexistent/path/preset.yml"));
    }

    @Test
    @DisplayName("PresetDefinition.toPersona 转换为 PersonaDefinition")
    void presetToPersona() {
        var loader = new YamlPresetLoader();
        var preset = loader.load("name: p\ndescription: d\nsystem_prompt: s\nkey: v\n");
        var persona = preset.toPersona();
        assertEquals("p", persona.name());
        assertEquals("d", persona.description());
        assertEquals("s", persona.systemPrompt());
        assertEquals("v", persona.attributes().get("key"));
    }

    @Test
    @DisplayName("PersonaDefinition 空名抛出异常")
    void personaBlankNameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new PersonaDefinition("", "d", "s", java.util.Map.of()));
    }
}
