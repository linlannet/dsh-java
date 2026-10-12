package net.linlan.dsh.domain.tool.fs;

import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GrepTool 正则内容检索")
class GrepToolTest {

    static final class StubFsPort implements IFsPort {
        final List<String> grepCalls = new ArrayList<>();
        final Map<String, List<String>> grepResults;

        StubFsPort(Map<String, List<String>> grepResults) {
            this.grepResults = grepResults;
        }

        @Override
        public String read(String path, int offset, int limit) { return ""; }

        @Override
        public void write(String path, String content) {}

        @Override
        public List<String> search(String directory, String pattern, int maxResults) { return List.of(); }

        @Override
        public boolean exists(String path) { return false; }

        @Override
        public List<DirEntry> listDir(String directory) { return List.of(); }

        @Override
        public List<String> grep(String directory, String pattern, String include, boolean isRegex, int maxResults) {
            grepCalls.add(directory + "|" + pattern + "|" + include + "|" + isRegex + "|" + maxResults);
            return grepResults.getOrDefault(pattern, List.of());
        }

        @Override
        public byte[] readBytes(String path) { return new byte[0]; }
    }

    @Test
    @DisplayName("grep 委托 IFsPort.grep 返回 path:line:content 格式结果")
    void grepDelegatesToPort() {
        var stub = new StubFsPort(Map.of(
                "TODO", List.of("src/A.java:10:TODO fix", "src/B.java:25:TODO refactor")));
        var tool = new GrepTool(stub);
        var result = tool.grep("TODO", "/project", false);
        assertEquals(2, result.size());
        assertEquals("src/A.java:10:TODO fix", result.get(0));
        assertEquals("src/B.java:25:TODO refactor", result.get(1));
        assertEquals("/project|TODO|null|false|100", stub.grepCalls.get(0));
    }

    @Test
    @DisplayName("grep 正则模式传递 isRegex=true")
    void grepRegexModePassesTrue() {
        var stub = new StubFsPort(Map.of("class\\s+\\w+", List.of("src/A.java:5:class Foo")));
        var tool = new GrepTool(stub);
        tool.grep("class\\s+\\w+", "/project", true);
        assertEquals("/project|class\\s+\\w+|null|true|100", stub.grepCalls.get(0));
    }

    @Test
    @DisplayName("grep 带自定义 maxResults 传递正确值")
    void grepWithCustomMaxResults() {
        var stub = new StubFsPort(Map.of("foo", List.of("a:1:foo")));
        var tool = new GrepTool(stub);
        tool.grep("foo", "/cwd", false, 50);
        assertEquals("/cwd|foo|null|false|50", stub.grepCalls.get(0));
    }

    @Test
    @DisplayName("grep 带 include 传递正确值")
    void grepWithInclude() {
        var stub = new StubFsPort(Map.of("foo", List.of("a.java:1:foo")));
        var tool = new GrepTool(stub);
        tool.grep("foo", "/cwd", "*.java", false, 100);
        assertEquals("/cwd|foo|*.java|false|100", stub.grepCalls.get(0));
    }

    @Test
    @DisplayName("空 pattern 抛出 IllegalArgumentException")
    void blankPatternThrows() {
        var stub = new StubFsPort(Map.of());
        var tool = new GrepTool(stub);
        assertThrows(IllegalArgumentException.class, () -> tool.grep("", "/cwd", false));
        assertThrows(IllegalArgumentException.class, () -> tool.grep("  ", "/cwd", true));
    }

    @Test
    @DisplayName("非正 maxResults 抛出 IllegalArgumentException")
    void nonPositiveMaxResultsThrow() {
        var stub = new StubFsPort(Map.of());
        var tool = new GrepTool(stub);
        assertThrows(IllegalArgumentException.class, () -> tool.grep("foo", "/cwd", false, 0));
        assertThrows(IllegalArgumentException.class, () -> tool.grep("foo", "/cwd", false, -5));
    }

    @Test
    @DisplayName("无匹配时返回空列表")
    void noMatchReturnsEmptyList() {
        var stub = new StubFsPort(Map.of());
        var tool = new GrepTool(stub);
        var result = tool.grep("nonexistent", "/project", true);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("null 参数抛出 NullPointerException")
    void nullArgumentsRejected() {
        var stub = new StubFsPort(Map.of());
        var tool = new GrepTool(stub);
        assertThrows(NullPointerException.class, () -> tool.grep(null, "/cwd", false));
        assertThrows(NullPointerException.class, () -> tool.grep("foo", null, false));
    }
}
