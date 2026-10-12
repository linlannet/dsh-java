package net.linlan.dsh.infrastructure.adapter.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import net.linlan.dsh.domain.lsp.model.valobj.LspQueryResult;
import net.linlan.dsh.domain.lsp.model.valobj.LspQueryType;
import net.linlan.dsh.domain.lsp.stdio.LspStdioProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("T2.031 StdioLspProvider stdio LSP 基础设施适配器")
class StdioLspProviderTest {

    private static final String LSP_STUB_SCRIPT = """
            import json, sys
            for line in sys.stdin:
                req = json.loads(line)
                method = req.get("method")
                rid = req.get("id")
                if method == "initialize":
                    result = {}
                elif method == "textDocument/definition":
                    result = [{"uri": "file:///def.ts", "range": {"start": {"line": 5, "character": 0}, "end": {"line": 5, "character": 3}}}]
                elif method == "textDocument/references":
                    result = [{"uri": "file:///r1.ts", "range": {"start": {"line": 1, "character": 0}, "end": {"line": 1, "character": 2}}}, {"uri": "file:///r2.ts", "range": {"start": {"line": 2, "character": 0}, "end": {"line": 2, "character": 2}}}]
                elif method == "textDocument/diagnostic":
                    result = [{"message": "diag-msg"}]
                else:
                    result = {}
                print(json.dumps({"jsonrpc": "2.0", "id": rid, "result": result}), flush=True)
            """;

    private static boolean python3Available() {
        try {
            Process p = new ProcessBuilder("python3", "--version").redirectErrorStream(true).start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String startStubServer() throws Exception {
        Path script = Files.createTempFile("lsp-stub-", ".py");
        Files.writeString(script, LSP_STUB_SCRIPT);
        script.toFile().deleteOnExit();
        return "python3 -u " + script;
    }

    @Test
    @DisplayName("构造函数创建未初始化的 delegate")
    void constructorCreatesUninitializedDelegate() {
        StdioLspProvider provider = new StdioLspProvider("some-command");
        LspStdioProvider delegate = provider.getDelegate();
        assertNotNull(delegate);
        assertFalse(delegate.isInitialized());
    }

    @Test
    @DisplayName("query DEFINITION 通过 stdio 返回定位")
    void queryDefinitionReturnsLocationViaStdio() throws Exception {
        Assumptions.assumeTrue(python3Available());
        StdioLspProvider provider = new StdioLspProvider(startStubServer());

        LspQueryResult result = provider.query(LspQueryType.DEFINITION, "test.ts", "", 0, 0);

        assertEquals(1, result.locations().size());
        assertEquals("file:///def.ts", result.locations().get(0).uri());
        assertEquals(5, result.locations().get(0).startLine());
        assertTrue(result.hoverText().isEmpty());
    }

    @Test
    @DisplayName("query REFERENCES 通过 stdio 返回多个定位")
    void queryReferencesReturnsMultipleLocationsViaStdio() throws Exception {
        Assumptions.assumeTrue(python3Available());
        StdioLspProvider provider = new StdioLspProvider(startStubServer());

        LspQueryResult result = provider.query(LspQueryType.REFERENCES, "test.ts", "", 1, 1);

        assertEquals(2, result.locations().size());
        assertEquals("file:///r1.ts", result.locations().get(0).uri());
        assertEquals("file:///r2.ts", result.locations().get(1).uri());
    }

    @Test
    @DisplayName("query IMPLEMENTATION 复用 definition 结果")
    void queryImplementationReusesDefinitionResult() throws Exception {
        Assumptions.assumeTrue(python3Available());
        StdioLspProvider provider = new StdioLspProvider(startStubServer());

        LspQueryResult result = provider.query(LspQueryType.IMPLEMENTATION, "test.ts", "", 0, 0);

        assertEquals(1, result.locations().size());
        assertEquals("file:///def.ts", result.locations().get(0).uri());
    }

    @Test
    @DisplayName("query HOVER 通过 stdio 返回诊断文本")
    void queryHoverReturnsDiagnosticsTextViaStdio() throws Exception {
        Assumptions.assumeTrue(python3Available());
        StdioLspProvider provider = new StdioLspProvider(startStubServer());

        LspQueryResult result = provider.query(LspQueryType.HOVER, "test.ts", "", 0, 0);

        assertTrue(result.hoverText().isPresent());
        assertEquals("diag-msg", result.hoverText().get());
        assertTrue(result.locations().isEmpty());
    }

    @Test
    @DisplayName("query 未初始化且 serverCommand 无效抛 RuntimeException")
    void queryWithInvalidServerCommandThrowsRuntimeException() {
        StdioLspProvider provider = new StdioLspProvider("nonexistent-lsp-command-xyz-123");
        assertThrows(RuntimeException.class, () ->
                provider.query(LspQueryType.DEFINITION, "test.ts", "", 0, 0));
    }

    @Test
    @DisplayName("StdioLspProvider 实现 ILspProviderPort")
    void implementsILspProviderPort() {
        StdioLspProvider provider = new StdioLspProvider("cmd");
        assertTrue(provider instanceof net.linlan.dsh.domain.lsp.adapter.port.ILspProviderPort);
    }
}
