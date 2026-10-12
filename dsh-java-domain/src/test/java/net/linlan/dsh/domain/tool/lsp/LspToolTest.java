package net.linlan.dsh.domain.tool.lsp;

import net.linlan.dsh.domain.lsp.ILspService;
import net.linlan.dsh.domain.lsp.model.valobj.LspLocation;
import net.linlan.dsh.domain.lsp.model.valobj.LspQueryResult;
import net.linlan.dsh.domain.lsp.model.valobj.LspQueryType;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T2.031 LspTool query 方法")
class LspToolTest {

    @Test
    @DisplayName("queryDefinition 返回定义位置列表")
    void queryDefinitionReturnsLocations() {
        StubLspService lsp = new StubLspService();
        lsp.setResult(LspQueryResult.ofLocations(List.of(
                new LspLocation("file:///src/Foo.java", 3, 4, 3, 7)
        )));
        StubFsPort fs = new StubFsPort("class Foo {}");
        LspTool tool = new LspTool(lsp, fs);

        List<LspLocation> result = tool.queryDefinition("/src/Bar.java", 1, 2);

        assertEquals(1, result.size());
        assertEquals("file:///src/Foo.java", result.get(0).uri());
        assertEquals(LspQueryType.DEFINITION, lsp.lastQueryType);
        assertEquals("/src/Bar.java", lsp.lastFilePath);
    }

    @Test
    @DisplayName("queryReferences 返回引用位置列表")
    void queryReferencesReturnsLocations() {
        StubLspService lsp = new StubLspService();
        lsp.setResult(LspQueryResult.ofLocations(List.of(
                new LspLocation("file:///src/A.java", 1, 0, 1, 3),
                new LspLocation("file:///src/B.java", 5, 2, 5, 5)
        )));
        StubFsPort fs = new StubFsPort("Foo x;");
        LspTool tool = new LspTool(lsp, fs);

        List<LspLocation> result = tool.queryReferences("/src/Foo.java", 0, 0);

        assertEquals(2, result.size());
        assertEquals(LspQueryType.REFERENCES, lsp.lastQueryType);
    }

    @Test
    @DisplayName("queryDiagnostics 返回诊断消息列表")
    void queryDiagnosticsReturnsMessages() {
        StubLspService lsp = new StubLspService();
        lsp.setResult(LspQueryResult.ofHover("semicolon missing\nunused variable"));
        StubFsPort fs = new StubFsPort("class Foo {");
        LspTool tool = new LspTool(lsp, fs);

        List<String> result = tool.queryDiagnostics("/src/Foo.java");

        assertEquals(2, result.size());
        assertEquals("semicolon missing", result.get(0));
        assertEquals("unused variable", result.get(1));
    }

    @Test
    @DisplayName("queryDefinition 无 provider 时返回空列表")
    void queryDefinitionEmptyWhenNoProvider() {
        StubLspService lsp = new StubLspService();
        lsp.setResult(null);
        StubFsPort fs = new StubFsPort("content");
        LspTool tool = new LspTool(lsp, fs);

        List<LspLocation> result = tool.queryDefinition("/src/Foo.java", 0, 0);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("queryReferences 无 provider 时返回空列表")
    void queryReferencesEmptyWhenNoProvider() {
        StubLspService lsp = new StubLspService();
        lsp.setResult(null);
        StubFsPort fs = new StubFsPort("content");
        LspTool tool = new LspTool(lsp, fs);

        List<LspLocation> result = tool.queryReferences("/src/Foo.java", 0, 0);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("queryDiagnostics 无 hover 时返回空列表")
    void queryDiagnosticsEmptyWhenNoHover() {
        StubLspService lsp = new StubLspService();
        lsp.setResult(LspQueryResult.ofLocations(List.of()));
        StubFsPort fs = new StubFsPort("content");
        LspTool tool = new LspTool(lsp, fs);

        List<String> result = tool.queryDiagnostics("/src/Foo.java");

        assertTrue(result.isEmpty());
    }

    static class StubLspService implements ILspService {
        LspQueryType lastQueryType;
        String lastFilePath;
        private LspQueryResult result;

        void setResult(LspQueryResult result) {
            this.result = result;
        }

        @Override
        public Optional<LspQueryResult> query(LspQueryType queryType, String filePath, String fileContent, int line, int character) {
            this.lastQueryType = queryType;
            this.lastFilePath = filePath;
            return Optional.ofNullable(result);
        }
    }

    static class StubFsPort implements IFsPort {
        private final String content;

        StubFsPort(String content) {
            this.content = content;
        }

        @Override
        public String read(String path, int offset, int limit) {
            return content;
        }

        @Override
        public void write(String path, String content) {}

        @Override
        public List<String> search(String directory, String pattern, int maxResults) {
            return List.of();
        }

        @Override
        public boolean exists(String path) {
            return true;
        }

        @Override
        public List<DirEntry> listDir(String directory) {
            return List.of();
        }

        @Override
        public List<String> grep(String directory, String pattern, String include, boolean isRegex, int maxResults) {
            return List.of();
        }

        @Override
        public byte[] readBytes(String path) {
            return new byte[0];
        }
    }
}
