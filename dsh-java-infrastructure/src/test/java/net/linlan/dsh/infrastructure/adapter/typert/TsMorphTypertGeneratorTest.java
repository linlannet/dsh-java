package net.linlan.dsh.infrastructure.adapter.typert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("T2.030 TsMorphTypertGenerator ts-morph 类型协议生成器")
class TsMorphTypertGeneratorTest {

    private static boolean nodeAvailable() {
        try {
            Process p = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("默认构造函数使用 node 可执行文件")
    void defaultConstructorUsesNode() {
        Assumptions.assumeTrue(nodeAvailable());
        TsMorphTypertGenerator generator = new TsMorphTypertGenerator();
        String output = generator.generate("--version");
        assertTrue(output.startsWith("v"));
    }

    @Test
    @DisplayName("构造函数 null/blank nodeExecutable 回退为 node")
    void nullNodeExecutableFallsBackToNode() {
        Assumptions.assumeTrue(nodeAvailable());
        TsMorphTypertGenerator generator = new TsMorphTypertGenerator(null, null);
        String output = generator.generate("--version");
        assertTrue(output.startsWith("v"));
    }

    @Test
    @DisplayName("generate null 路径抛 IllegalArgumentException")
    void generateNullPathThrows() {
        TsMorphTypertGenerator generator = new TsMorphTypertGenerator();
        assertThrows(IllegalArgumentException.class, () -> generator.generate(null));
    }

    @Test
    @DisplayName("generate 空/blank 路径抛 IllegalArgumentException")
    void generateBlankPathThrows() {
        TsMorphTypertGenerator generator = new TsMorphTypertGenerator();
        assertThrows(IllegalArgumentException.class, () -> generator.generate(""));
        assertThrows(IllegalArgumentException.class, () -> generator.generate("   "));
    }

    @Test
    @DisplayName("generate 不存在的 node 可执行文件抛 RuntimeException")
    void generateWithNonexistentNodeThrowsRuntimeException() {
        TsMorphTypertGenerator generator = new TsMorphTypertGenerator("nonexistent-node-xyz-123", "");
        assertThrows(RuntimeException.class, () -> generator.generate("/project"));
    }

    @Test
    @DisplayName("generate 带 generatorScriptPath 构建命令 [node, script, projectPath]")
    void generateWithScriptPathBuildsCommand() {
        Assumptions.assumeTrue(nodeAvailable());
        TsMorphTypertGenerator generator = new TsMorphTypertGenerator("node", "-e");
        String output = generator.generate("console.log('via-script-flag')");
        assertEquals("via-script-flag", output);
    }

    @Test
    @DisplayName("TsMorphTypertGenerator 实现 ITypertGeneratorPort")
    void implementsITypertGeneratorPort() {
        TsMorphTypertGenerator generator = new TsMorphTypertGenerator();
        assertTrue(generator instanceof net.linlan.dsh.domain.typert.generator.adapter.port.ITypertGeneratorPort);
    }
}
