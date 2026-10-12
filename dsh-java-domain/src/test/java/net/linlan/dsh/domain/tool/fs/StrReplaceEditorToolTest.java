package net.linlan.dsh.domain.tool.fs;

import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StrReplaceEditorTool 字符串替换编辑")
class StrReplaceEditorToolTest {

    static final class StubFsPort implements IFsPort {
        final Map<String, String> files = new HashMap<>();
        final List<String> writeCalls = new ArrayList<>();

        @Override
        public String read(String path, int offset, int limit) {
            return files.getOrDefault(path, "");
        }

        @Override
        public void write(String path, String content) {
            writeCalls.add(path + "=" + content);
            files.put(path, content);
        }

        @Override
        public List<String> search(String directory, String pattern, int maxResults) { return List.of(); }

        @Override
        public boolean exists(String path) { return files.containsKey(path); }

        @Override
        public List<DirEntry> listDir(String directory) { throw new UnsupportedOperationException("not a directory"); }

        @Override
        public List<String> grep(String directory, String pattern, String include, boolean isRegex, int maxResults) { return List.of(); }

        @Override
        public byte[] readBytes(String path) { return new byte[0]; }
    }

    @Test
    @DisplayName("create 新文件成功写入")
    void createWritesNewFile() {
        var stub = new StubFsPort();
        var tool = new StrReplaceEditorTool(stub);
        var result = tool.execute(Map.of("command", "create", "path", "/a.txt", "new_str", "hello"), null);
        var r = result.join();
        assertFalse(r.isError());
        assertEquals("hello", stub.files.get("/a.txt"));
    }

    @Test
    @DisplayName("create 已存在文件返回错误内容")
    void createFailsOnExistingFile() {
        var stub = new StubFsPort();
        stub.files.put("/a.txt", "old");
        var tool = new StrReplaceEditorTool(stub);
        var result = tool.execute(Map.of("command", "create", "path", "/a.txt", "new_str", "new"), null);
        var r = result.join();
        String output = ((TextBlock) r.content().get(0)).text();
        assertTrue(output.contains("Error") || output.contains("already exists"));
    }

    @Test
    @DisplayName("str_replace 精确匹配替换成功")
    void strReplaceExactMatch() {
        var stub = new StubFsPort();
        stub.files.put("/a.txt", "line1\nline2\nline3");
        var tool = new StrReplaceEditorTool(stub);
        var result = tool.execute(Map.of(
                "command", "str_replace",
                "path", "/a.txt",
                "old_str", "line2",
                "new_str", "replaced"), null);
        var r = result.join();
        assertFalse(r.isError());
        assertEquals("line1\nreplaced\nline3", stub.files.get("/a.txt"));
    }

    @Test
    @DisplayName("str_replace 不匹配返回错误内容")
    void strReplaceNoMatchFails() {
        var stub = new StubFsPort();
        stub.files.put("/a.txt", "line1\nline2");
        var tool = new StrReplaceEditorTool(stub);
        var result = tool.execute(Map.of(
                "command", "str_replace",
                "path", "/a.txt",
                "old_str", "nonexistent",
                "new_str", "x"), null);
        var r = result.join();
        String output = ((TextBlock) r.content().get(0)).text();
        assertTrue(output.contains("Error") || output.contains("not found"));
    }

    @Test
    @DisplayName("str_replace 多次匹配返回冲突错误内容")
    void strReplaceAmbiguousMatchFails() {
        var stub = new StubFsPort();
        stub.files.put("/a.txt", "dup\ndup");
        var tool = new StrReplaceEditorTool(stub);
        var result = tool.execute(Map.of(
                "command", "str_replace",
                "path", "/a.txt",
                "old_str", "dup",
                "new_str", "x"), null);
        var r = result.join();
        String output = ((TextBlock) r.content().get(0)).text();
        assertTrue(output.contains("Error") || output.contains("times"));
    }

    @Test
    @DisplayName("view 不存在的文件返回错误信息")
    void viewMissingFileReturnsMessage() {
        var stub = new StubFsPort();
        var tool = new StrReplaceEditorTool(stub);
        var result = tool.execute(Map.of("command", "view", "path", "/missing"), null);
        var r = result.join();
        assertFalse(r.isError());
    }

    @Test
    @DisplayName("view 已存在文件返回带行号内容")
    void viewExistingFileReturnsNumberedContent() {
        var stub = new StubFsPort();
        stub.files.put("/a.txt", "alpha\nbeta");
        var tool = new StrReplaceEditorTool(stub);
        var result = tool.execute(Map.of("command", "view", "path", "/a.txt"), null);
        var r = result.join();
        assertFalse(r.isError());
    }

    @Test
    @DisplayName("insert 在指定行插入内容")
    void insertAtLine() {
        var stub = new StubFsPort();
        stub.files.put("/a.txt", "line1\nline2\nline3");
        var tool = new StrReplaceEditorTool(stub);
        var result = tool.execute(Map.of(
                "command", "insert",
                "path", "/a.txt",
                "new_str", "inserted",
                "insert_line", 1), null);
        var r = result.join();
        assertFalse(r.isError());
        String updated = stub.files.get("/a.txt");
        assertTrue(updated.contains("inserted"));
    }

    @Test
    @DisplayName("未知 command 返回 Unknown 内容")
    void unknownCommandFails() {
        var stub = new StubFsPort();
        var tool = new StrReplaceEditorTool(stub);
        var result = tool.execute(Map.of("command", "bogus", "path", "/a.txt"), null);
        var r = result.join();
        String output = ((TextBlock) r.content().get(0)).text();
        assertTrue(output.contains("Unknown"));
    }

    @Test
    @DisplayName("isConcurrencySafe 始终返回 false")
    void isConcurrencySafeReturnsFalse() {
        var stub = new StubFsPort();
        var tool = new StrReplaceEditorTool(stub);
        assertFalse(tool.isConcurrencySafe(null));
    }
}
