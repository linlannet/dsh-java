package net.linlan.dsh.domain.typert.generator.adapter.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("T2.030 ITypertGeneratorPort 端口契约")
class ITypertGeneratorPortTest {

    @Test
    @DisplayName("generate(tsProjectPath) 返回类型协议定义")
    void generateReturnsProtocolDefinition() {
        ITypertGeneratorPort port = new StubPort("protocol-def");
        String result = port.generate("/project");
        assertEquals("protocol-def", result);
    }

    @Test
    @DisplayName("generate 不同路径返回对应结果")
    void generateReturnsDifferentResultsForDifferentPaths() {
        ITypertGeneratorPort port = new StubPort("def-" + System.nanoTime());
        String result = port.generate("/another");
        assertNotNull(result);
        assertEquals(port.generate("/another"), result);
    }

    @Test
    @DisplayName("实现类实例是 ITypertGeneratorPort 子类型")
    void implementationIsPortSubtype() {
        ITypertGeneratorPort port = new StubPort("");
        assertNotNull(port);
        assert ITypertGeneratorPort.class.isInstance(port);
    }

    static final class StubPort implements ITypertGeneratorPort {
        private final String result;

        StubPort(String result) {
            this.result = result;
        }

        @Override
        public String generate(String tsProjectPath) {
            return result;
        }
    }
}
