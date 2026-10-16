package net.linlan.dsh.domain.boot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigEditor 配置编辑")
class ConfigEditorTest {

    @Test
    @DisplayName("set/get 按当前 profile 存取")
    void setGet() {
        var editor = new ConfigEditor("prod");
        editor.set("port", "8080");
        assertEquals("8080", editor.get("port"));
    }

    @Test
    @DisplayName("不同 profile 隔离")
    void profileIsolation() {
        var prod = new ConfigEditor("prod");
        var dev = new ConfigEditor("dev");

        prod.set("port", "8080");
        dev.set("port", "3000");

        assertEquals("8080", prod.get("port"));
        assertEquals("3000", dev.get("port"));
    }

    @Test
    @DisplayName("remove 删除配置项")
    void remove() {
        var editor = new ConfigEditor("default");
        editor.set("key", "value");
        editor.remove("key");
        assertNull(editor.get("key"));
    }

    @Test
    @DisplayName("snapshot 返回当前 profile 全部配置")
    void snapshot() {
        var editor = new ConfigEditor("prod");
        editor.set("port", "8080");
        editor.set("host", "0.0.0.0");

        var snap = editor.snapshot();
        assertEquals("8080", snap.get("port"));
        assertEquals("0.0.0.0", snap.get("host"));
    }

    @Test
    @DisplayName("activeProfile 返回当前 profile")
    void activeProfile() {
        var editor = new ConfigEditor("staging");
        assertEquals("staging", editor.activeProfile());
    }
}
