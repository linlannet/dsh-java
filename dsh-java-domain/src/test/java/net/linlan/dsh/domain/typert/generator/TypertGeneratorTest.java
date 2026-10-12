package net.linlan.dsh.domain.typert.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.linlan.dsh.domain.typert.generator.adapter.port.ITypertGeneratorPort;
import org.junit.jupiter.api.Test;

class TypertGeneratorTest {

    @Test
    void shouldGenerateFromValidPath() {
        ITypertGeneratorPort port = new StubPort("protocol-definition");
        TypertGenerator generator = new TypertGenerator(port);

        String result = generator.generate("/project");

        assertEquals("protocol-definition", result);
    }

    @Test
    void shouldRejectBlankPath() {
        ITypertGeneratorPort port = new StubPort("result");
        TypertGenerator generator = new TypertGenerator(port);

        assertThrows(IllegalArgumentException.class, () -> generator.generate("  "));
    }

    @Test
    void shouldRejectNullPath() {
        ITypertGeneratorPort port = new StubPort("result");
        TypertGenerator generator = new TypertGenerator(port);

        assertThrows(IllegalArgumentException.class, () -> generator.generate(null));
    }

    @Test
    void shouldRejectNullPort() {
        assertThrows(IllegalArgumentException.class, () -> new TypertGenerator(null));
    }

    @Test
    void shouldReturnEmptyWhenPortReturnsBlank() {
        ITypertGeneratorPort port = new StubPort("");
        TypertGenerator generator = new TypertGenerator(port);

        String result = generator.generate("/project");

        assertEquals("", result);
    }

    @Test
    void shouldReturnEmptyWhenPortReturnsNull() {
        ITypertGeneratorPort port = new StubPort(null);
        TypertGenerator generator = new TypertGenerator(port);

        String result = generator.generate("/project");

        assertEquals("", result);
    }

    @Test
    void shouldGenerateWithOptions() {
        ITypertGeneratorPort port = new StubPort("base-protocol");
        TypertGenerator generator = new TypertGenerator(port);

        String result = generator.generate("/project", Map.of("format", "json"));

        assertNotNull(result);
        assertTrue(result.contains("base-protocol"));
        assertTrue(result.contains("format=json"));
    }

    @Test
    void shouldGenerateWithNullOptionsReturningRaw() {
        ITypertGeneratorPort port = new StubPort("raw");
        TypertGenerator generator = new TypertGenerator(port);

        String result = generator.generate("/project", null);

        assertEquals("raw", result);
    }

    @Test
    void shouldGenerateWithEmptyOptionsReturningRaw() {
        ITypertGeneratorPort port = new StubPort("raw");
        TypertGenerator generator = new TypertGenerator(port);

        String result = generator.generate("/project", Map.of());

        assertEquals("raw", result);
    }

    @Test
    void shouldRejectBlankPathWithOptions() {
        ITypertGeneratorPort port = new StubPort("result");
        TypertGenerator generator = new TypertGenerator(port);

        assertThrows(IllegalArgumentException.class, () ->
                generator.generate("  ", Map.of()));
    }

    static class StubPort implements ITypertGeneratorPort {
        final String result;

        StubPort(String result) {
            this.result = result;
        }

        @Override
        public String generate(String tsProjectPath) {
            return result;
        }
    }
}
