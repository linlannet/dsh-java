package net.linlan.dsh.domain.agent.service;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.model.valobj.ToolSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScopedSystemPromptAssembler — waterfall 集成")
class ScopedSystemPromptAssemblerWaterfallTest {

    @Test
    @DisplayName("多 section 按 order 依次拼接")
    void multipleSectionsConcat() throws Exception {
        var assembler = new ScopedSystemPromptAssembler("id", "persona", List.of());
        assembler.registerSection(new ScopedSystemPromptAssembler.PromptSection("a", 10, "Section A"));
        assembler.registerSection(new ScopedSystemPromptAssembler.PromptSection("b", 20, "Section B"));
        assembler.registerSection(new ScopedSystemPromptAssembler.PromptSection("c", 5, "Section C"));

        var result = assembler.assemble("agent-1", null, "/tmp", List.of()).get();

        assertTrue(result.system().contains("Section C"));
        assertTrue(result.system().contains("Section A"));
        assertTrue(result.system().contains("Section B"));
        int cPos = result.system().indexOf("Section C");
        int aPos = result.system().indexOf("Section A");
        int bPos = result.system().indexOf("Section B");
        assertTrue(cPos < aPos, "C(order=5) before A(order=10)");
        assertTrue(aPos < bPos, "A(order=10) before B(order=20)");
    }

    @Test
    @DisplayName("complete=true section 终止传播，后续 section 不执行")
    void completeSectionTerminates() throws Exception {
        var assembler = new ScopedSystemPromptAssembler("id", "persona", List.of());
        assembler.registerSection(new ScopedSystemPromptAssembler.PromptSection("normal", 10, "Normal text"));
        assembler.registerSection(new ScopedSystemPromptAssembler.PromptSection("override", 20, "OVERRIDE", true));
        assembler.registerSection(new ScopedSystemPromptAssembler.PromptSection("after", 30, "After text"));

        var result = assembler.assemble("agent-1", null, "/tmp", List.of()).get();

        assertTrue(result.system().contains("OVERRIDE"));
        assertFalse(result.system().contains("After text"));
    }

    @Test
    @DisplayName("变量插值 {{var}} 仍工作")
    void variableInterpolation() throws Exception {
        var assembler = new ScopedSystemPromptAssembler("id", "persona", List.of());
        assembler.registerSection(new ScopedSystemPromptAssembler.PromptSection("greeting", 10, "Hello {{name}}!"));
        assembler.registerVariable("name", ctx -> "World");

        var result = assembler.assemble("agent-1", null, "/tmp", List.of()).get();

        assertTrue(result.system().contains("Hello World!"));
    }

    @Test
    @DisplayName("v0.1.8 回归：identity 和 persona section 存在")
    void regressionIdentityAndPersona() throws Exception {
        var assembler = new ScopedSystemPromptAssembler("My Harness", "My Persona", List.of());

        var result = assembler.assemble("agent-1", null, "/tmp", List.of()).get();

        assertTrue(result.system().contains("My Harness"));
        assertTrue(result.system().contains("My Persona"));
    }

    @Test
    @DisplayName("v0.1.8 回归：默认 identity 存在")
    void regressionDefaultIdentity() throws Exception {
        var assembler = new ScopedSystemPromptAssembler();

        var result = assembler.assemble("agent-1", null, "/tmp", List.of()).get();

        assertTrue(result.system().contains("DeepSeek Harness"));
    }

    @Test
    @DisplayName("v0.1.8 回归：context 在 runtime 未抑制时出现")
    void regressionContext() throws Exception {
        var assembler = new ScopedSystemPromptAssembler("id", "persona", List.of());
        assembler.registerContext(new ScopedSystemPromptAssembler.PromptContext("time", 10, "Current time: 2026-10-09"));

        var result = assembler.assemble("agent-1", null, "/tmp", List.of()).get();

        assertTrue(result.system().contains("Current time: 2026-10-09"));
    }

    @Test
    @DisplayName("v0.1.8 回归：suppressRuntimeContext 后 context 不出现")
    void regressionSuppressContext() throws Exception {
        var assembler = new ScopedSystemPromptAssembler("id", "persona", List.of());
        assembler.registerContext(new ScopedSystemPromptAssembler.PromptContext("time", 10, "Current time: 2026-10-09"));
        assembler.suppressRuntimeContext();

        var result = assembler.assemble("agent-1", null, "/tmp", List.of()).get();

        assertFalse(result.system().contains("Current time: 2026-10-09"));
    }
}
