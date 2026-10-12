package net.linlan.dsh.domain.lsp.stdio;

import net.linlan.dsh.domain.lsp.model.valobj.LspLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T2.031 LspStdioProvider")
class LspStdioProviderTest {

    @Test
    @DisplayName("未初始化时 definition 抛 IllegalStateException")
    void definitionThrowsWhenNotInitialized() {
        LspStdioProvider provider = new LspStdioProvider();
        assertThrows(IllegalStateException.class, () -> provider.definition("test.java", 0, 0));
    }

    @Test
    @DisplayName("未初始化时 references 抛 IllegalStateException")
    void referencesThrowsWhenNotInitialized() {
        LspStdioProvider provider = new LspStdioProvider();
        assertThrows(IllegalStateException.class, () -> provider.references("test.java", 0, 0));
    }

    @Test
    @DisplayName("未初始化时 diagnostics 抛 IllegalStateException")
    void diagnosticsThrowsWhenNotInitialized() {
        LspStdioProvider provider = new LspStdioProvider();
        assertThrows(IllegalStateException.class, () -> provider.diagnostics("test.java"));
    }

    @Test
    @DisplayName("isInitialized 初始为 false")
    void notInitializedByDefault() {
        LspStdioProvider provider = new LspStdioProvider();
        assertFalse(provider.isInitialized());
    }

    @Test
    @DisplayName("注入 reader/writer 后 isInitialized 为 true")
    void initializedWithInjectedStreams() {
        BufferedReader reader = new BufferedReader(new StringReader(""));
        BufferedWriter writer = new BufferedWriter(new StringWriter());
        LspStdioProvider provider = new LspStdioProvider(reader, writer);
        assertTrue(provider.isInitialized());
    }

    @Test
    @DisplayName("definition 解析 JSON-RPC 响应返回位置")
    void definitionParsesResponse() {
        String response = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":[{\"uri\":\"file:///src/Foo.java\",\"range\":{\"start\":{\"line\":3,\"character\":4},\"end\":{\"line\":3,\"character\":7}}}]}";
        BufferedReader reader = new BufferedReader(new StringReader(response));
        BufferedWriter writer = new BufferedWriter(new StringWriter());
        LspStdioProvider provider = new LspStdioProvider(reader, writer);

        Optional<LspLocation> loc = provider.definition("/src/Bar.java", 1, 2);

        assertTrue(loc.isPresent());
        assertEquals("file:///src/Foo.java", loc.get().uri());
        assertEquals(3, loc.get().startLine());
        assertEquals(4, loc.get().startCol());
    }

    @Test
    @DisplayName("references 解析 JSON-RPC 响应返回位置列表")
    void referencesParsesResponse() {
        String response = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":[" +
                "{\"uri\":\"file:///src/A.java\",\"range\":{\"start\":{\"line\":1,\"character\":0},\"end\":{\"line\":1,\"character\":3}}}," +
                "{\"uri\":\"file:///src/B.java\",\"range\":{\"start\":{\"line\":5,\"character\":2},\"end\":{\"line\":5,\"character\":5}}}" +
                "]}";
        BufferedReader reader = new BufferedReader(new StringReader(response));
        BufferedWriter writer = new BufferedWriter(new StringWriter());
        LspStdioProvider provider = new LspStdioProvider(reader, writer);

        List<LspLocation> refs = provider.references("/src/Foo.java", 0, 0);

        assertEquals(2, refs.size());
        assertEquals("file:///src/A.java", refs.get(0).uri());
        assertEquals("file:///src/B.java", refs.get(1).uri());
    }

    @Test
    @DisplayName("diagnostics 解析 JSON-RPC 响应返回消息列表")
    void diagnosticsParsesResponse() {
        String response = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":[" +
                "{\"message\":\"semicolon missing\"}," +
                "{\"message\":\"unused variable\"}" +
                "]}";
        BufferedReader reader = new BufferedReader(new StringReader(response));
        BufferedWriter writer = new BufferedWriter(new StringWriter());
        LspStdioProvider provider = new LspStdioProvider(reader, writer);

        List<String> diags = provider.diagnostics("/src/Foo.java");

        assertEquals(2, diags.size());
        assertEquals("semicolon missing", diags.get(0));
        assertEquals("unused variable", diags.get(1));
    }

    @Test
    @DisplayName("definition 空结果返回 Optional.empty")
    void definitionEmptyResult() {
        String response = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":[]}";
        BufferedReader reader = new BufferedReader(new StringReader(response));
        BufferedWriter writer = new BufferedWriter(new StringWriter());
        LspStdioProvider provider = new LspStdioProvider(reader, writer);

        Optional<LspLocation> loc = provider.definition("/src/Foo.java", 0, 0);

        assertTrue(loc.isEmpty());
    }
}
