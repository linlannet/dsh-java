package net.linlan.dsh.domain.agent.context.service;

import net.linlan.dsh.domain.agent.context.model.valobj.Instructions;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentsMdLoader 层级加载与合并")
class AgentsMdLoaderTest {

    @Test
    @DisplayName("空路径返回空指令")
    void emptyPathReturnsEmpty() {
        var loader = new AgentsMdLoader(new StubFs(Map.of()));
        var result = loader.load("");
        assertTrue(result.directives().isEmpty());
    }

    @Test
    @DisplayName("单层 AGENTS.md 加载并解析指令")
    void singleLayerLoad() {
        Map<String, String> files = new HashMap<>();
        files.put("/root/AGENTS.md", "language: zh-CN\neditor: vim");
        var loader = new AgentsMdLoader(new StubFs(files));
        var result = loader.load("/root");
        assertEquals("zh-CN", result.directives().get("language"));
        assertEquals("vim", result.directives().get("editor"));
    }

    @Test
    @DisplayName("多层 AGENTS.md 子级覆盖父级同键")
    void multiLayerChildOverridesParent() {
        Map<String, String> files = new HashMap<>();
        files.put("/root/AGENTS.md", "language: en\neditor: vim");
        files.put("/root/project/AGENTS.md", "language: zh-CN");
        var loader = new AgentsMdLoader(new StubFs(files));
        var result = loader.load("/root/project");
        assertEquals("zh-CN", result.directives().get("language"));
        assertEquals("vim", result.directives().get("editor"));
    }

    @Test
    @DisplayName("无 AGENTS.md 时返回空指令")
    void noAgentsMdReturnsEmpty() {
        var loader = new AgentsMdLoader(new StubFs(Map.of()));
        var result = loader.load("/root");
        assertTrue(result.directives().isEmpty());
    }

    @Test
    @DisplayName("merge 空列表返回空指令")
    void mergeEmptyList() {
        var loader = new AgentsMdLoader(new StubFs(Map.of()));
        var result = loader.merge(java.util.List.of());
        assertTrue(result.directives().isEmpty());
    }

    @Test
    @DisplayName("注释行被跳过")
    void commentLinesSkipped() {
        Map<String, String> files = new HashMap<>();
        files.put("/root/AGENTS.md", "# comment\nkey: value\n// another comment");
        var loader = new AgentsMdLoader(new StubFs(files));
        var result = loader.load("/root");
        assertEquals(1, result.directives().size());
        assertEquals("value", result.directives().get("key"));
    }

    @Test
    @DisplayName("null fsPort 抛异常")
    void nullFsPortRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AgentsMdLoader(null));
    }

    static final class StubFs implements IFsPort {
        private final Map<String, String> files;

        StubFs(Map<String, String> files) {
            this.files = files;
        }

        @Override
        public String read(String path, int offset, int limit) {
            return files.getOrDefault(path, "");
        }

        @Override
        public void write(String path, String content) {}

        @Override
        public java.util.List<String> search(String directory, String pattern, int maxResults) {
            return java.util.List.of();
        }

        @Override
        public boolean exists(String path) {
            return files.containsKey(path);
        }

        @Override
        public java.util.List<DirEntry> listDir(String directory) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<String> grep(String directory, String pattern, String include, boolean isRegex, int maxResults) {
            return java.util.List.of();
        }

        @Override
        public byte[] readBytes(String path) {
            String content = files.get(path);
            return content == null ? new byte[0] : content.getBytes();
        }
    }
}
