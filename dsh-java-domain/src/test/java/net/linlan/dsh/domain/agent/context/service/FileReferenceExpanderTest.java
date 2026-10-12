package net.linlan.dsh.domain.agent.context.service;

import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileReferenceExpander @file 内联展开")
class FileReferenceExpanderTest {

    @Test
    @DisplayName("@file 引用被替换为文件内容")
    void atFileReplacedWithContent() {
        Map<String, String> files = new HashMap<>();
        files.put("/tmp/readme.txt", "Hello World");
        var expander = new FileReferenceExpander(new StubFs(files));
        String result = expander.expand("See @readme.txt", "/tmp");
        assertEquals("See Hello World", result);
    }

    @Test
    @DisplayName("文件不存在时保留原引用")
    void nonExistentFileKeepsReference() {
        var expander = new FileReferenceExpander(new StubFs(Map.of()));
        String result = expander.expand("See @nonexistent.txt", "/tmp");
        assertEquals("See @/tmp/nonexistent.txt", result);
    }

    @Test
    @DisplayName("多个 @file 引用全部展开")
    void multipleAtFileReferencesExpanded() {
        Map<String, String> files = new HashMap<>();
        files.put("/tmp/a.txt", "ContentA");
        files.put("/tmp/b.txt", "ContentB");
        var expander = new FileReferenceExpander(new StubFs(files));
        String result = expander.expand("@a.txt and @b.txt", "/tmp");
        assertEquals("ContentA and ContentB", result);
    }

    @Test
    @DisplayName("绝对路径 @file 直接解析")
    void absolutePathResolved() {
        Map<String, String> files = new HashMap<>();
        files.put("/etc/config", "config-content");
        var expander = new FileReferenceExpander(new StubFs(files));
        String result = expander.expand("config: @/etc/config");
        assertEquals("config: config-content", result);
    }

    @Test
    @DisplayName("无 @file 引用时文本不变")
    void noAtFileReferenceUnchanged() {
        var expander = new FileReferenceExpander(new StubFs(Map.of()));
        String result = expander.expand("plain text without references");
        assertEquals("plain text without references", result);
    }

    @Test
    @DisplayName("空文本返回空")
    void emptyTextReturnsEmpty() {
        var expander = new FileReferenceExpander(new StubFs(Map.of()));
        assertEquals("", expander.expand(""));
        assertNull(expander.expand(null));
    }

    @Test
    @DisplayName("null fsPort 抛异常")
    void nullFsPortRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FileReferenceExpander(null));
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
