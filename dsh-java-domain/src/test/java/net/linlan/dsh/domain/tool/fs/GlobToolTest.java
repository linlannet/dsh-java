package net.linlan.dsh.domain.tool.fs;

import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobTool glob 模式文件匹配")
class GlobToolTest {

    static final class StubFsPort implements IFsPort {
        final List<String> searchCalls = new ArrayList<>();
        final Map<String, List<String>> searchResults;

        StubFsPort(Map<String, List<String>> searchResults) {
            this.searchResults = searchResults;
        }

        @Override
        public String read(String path, int offset, int limit) { return ""; }

        @Override
        public void write(String path, String content) {}

        @Override
        public List<String> search(String directory, String pattern, int maxResults) {
            searchCalls.add(directory + "|" + pattern + "|" + maxResults);
            return searchResults.getOrDefault(pattern, List.of());
        }

        @Override
        public boolean exists(String path) { return false; }

        @Override
        public List<DirEntry> listDir(String directory) { return List.of(); }

        @Override
        public List<String> grep(String directory, String pattern, String include, boolean isRegex, int maxResults) {
            return List.of();
        }

        @Override
        public byte[] readBytes(String path) { return new byte[0]; }
    }

    @Test
    @DisplayName("glob 委托 IFsPort.search 并返回结果")
    void globDelegatesToSearch() {
        var stub = new StubFsPort(Map.of("*.java", List.of("src/A.java", "src/B.java")));
        var tool = new GlobTool(stub);
        var result = tool.glob("*.java", "/project");
        assertEquals(List.of("src/A.java", "src/B.java"), result);
        assertEquals(1, stub.searchCalls.size());
        assertEquals("/project|*.java|100", stub.searchCalls.get(0));
    }

    @Test
    @DisplayName("glob 带自定义 maxResults 传递正确值")
    void globWithCustomMaxResults() {
        var stub = new StubFsPort(Map.of("*.ts", List.of("a.ts")));
        var tool = new GlobTool(stub);
        tool.glob("*.ts", "/cwd", 50);
        assertEquals("/cwd|*.ts|50", stub.searchCalls.get(0));
    }

    @Test
    @DisplayName("空 pattern 抛出 IllegalArgumentException")
    void blankPatternThrows() {
        var stub = new StubFsPort(Map.of());
        var tool = new GlobTool(stub);
        assertThrows(IllegalArgumentException.class, () -> tool.glob("", "/cwd"));
        assertThrows(IllegalArgumentException.class, () -> tool.glob("  ", "/cwd"));
    }

    @Test
    @DisplayName("空 cwd 抛出 IllegalArgumentException")
    void blankCwdThrows() {
        var stub = new StubFsPort(Map.of());
        var tool = new GlobTool(stub);
        assertThrows(IllegalArgumentException.class, () -> tool.glob("*.java", ""));
    }

    @Test
    @DisplayName("非正 maxResults 抛出 IllegalArgumentException")
    void nonPositiveMaxResultsThrow() {
        var stub = new StubFsPort(Map.of());
        var tool = new GlobTool(stub);
        assertThrows(IllegalArgumentException.class, () -> tool.glob("*.java", "/cwd", 0));
        assertThrows(IllegalArgumentException.class, () -> tool.glob("*.java", "/cwd", -1));
    }

    @Test
    @DisplayName("无匹配时返回空列表")
    void noMatchReturnsEmptyList() {
        var stub = new StubFsPort(Map.of());
        var tool = new GlobTool(stub);
        var result = tool.glob("*.xyz", "/project");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("null 参数抛出 NullPointerException")
    void nullArgumentsRejected() {
        var stub = new StubFsPort(Map.of());
        var tool = new GlobTool(stub);
        assertThrows(NullPointerException.class, () -> tool.glob(null, "/cwd"));
        assertThrows(NullPointerException.class, () -> tool.glob("*.java", null));
    }
}
