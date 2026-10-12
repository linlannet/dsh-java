package net.linlan.dsh.domain.task.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T3.033 PermissionPreset 权限预设")
class PermissionPresetTest {

    @Test
    @DisplayName("full_access 预设允许所有工具与路径")
    void fullAccessPreset() {
        var preset = new PermissionPreset();
        var result = preset.apply(PermissionPreset.PRESET_FULL_ACCESS, PermissionPreset.PermissionConfig.empty());
        assertEquals(PermissionPreset.PRESET_FULL_ACCESS, result.presetName());
        assertEquals(List.of("*"), result.config().allowedTools());
        assertTrue(result.config().deniedTools().isEmpty());
        assertEquals(List.of("*"), result.config().allowedPaths());
    }

    @Test
    @DisplayName("read_only 预设拒绝写工具")
    void readOnlyPreset() {
        var preset = new PermissionPreset();
        var result = preset.apply(PermissionPreset.PRESET_READ_ONLY, PermissionPreset.PermissionConfig.empty());
        assertTrue(result.config().allowedTools().contains("read"));
        assertTrue(result.config().deniedTools().contains("write"));
        assertTrue(result.config().deniedTools().contains("exec"));
    }

    @Test
    @DisplayName("safe 预设拒绝危险路径")
    void safePreset() {
        var preset = new PermissionPreset();
        var result = preset.apply(PermissionPreset.PRESET_SAFE, PermissionPreset.PermissionConfig.empty());
        assertTrue(result.config().deniedPaths().contains("/etc/**"));
        assertTrue(result.config().deniedPaths().contains("~/.ssh/**"));
        assertTrue(result.config().deniedTools().contains("exec"));
    }

    @Test
    @DisplayName("safe 预设保留已有 allowedPaths")
    void safePresetKeepsExistingPaths() {
        var preset = new PermissionPreset();
        var input = new PermissionPreset.PermissionConfig(
                List.of("read"), List.of(), List.of("/workspace/**"), List.of());
        var result = preset.apply(PermissionPreset.PRESET_SAFE, input);
        assertEquals(List.of("/workspace/**"), result.config().allowedPaths());
    }

    @Test
    @DisplayName("未知预设抛异常")
    void unknownPresetThrows() {
        var preset = new PermissionPreset();
        assertThrows(IllegalArgumentException.class,
                () -> preset.apply("unknown", PermissionPreset.PermissionConfig.empty()));
    }

    @Test
    @DisplayName("null presetName 抛异常")
    void nullPresetNameThrows() {
        var preset = new PermissionPreset();
        assertThrows(IllegalArgumentException.class,
                () -> preset.apply(null, PermissionPreset.PermissionConfig.empty()));
    }

    @Test
    @DisplayName("null config 抛异常")
    void nullConfigThrows() {
        var preset = new PermissionPreset();
        assertThrows(IllegalArgumentException.class,
                () -> preset.apply(PermissionPreset.PRESET_FULL_ACCESS, null));
    }

    @Test
    @DisplayName("isKnownPreset 识别已知预设")
    void isKnownPreset() {
        var preset = new PermissionPreset();
        assertTrue(preset.isKnownPreset(PermissionPreset.PRESET_FULL_ACCESS));
        assertTrue(preset.isKnownPreset(PermissionPreset.PRESET_READ_ONLY));
        assertTrue(preset.isKnownPreset(PermissionPreset.PRESET_SAFE));
        assertFalse(preset.isKnownPreset("unknown"));
    }

    @Test
    @DisplayName("empty config 所有列表为空")
    void emptyConfig() {
        var config = PermissionPreset.PermissionConfig.empty();
        assertTrue(config.allowedTools().isEmpty());
        assertTrue(config.deniedTools().isEmpty());
        assertTrue(config.allowedPaths().isEmpty());
        assertTrue(config.deniedPaths().isEmpty());
    }
}
