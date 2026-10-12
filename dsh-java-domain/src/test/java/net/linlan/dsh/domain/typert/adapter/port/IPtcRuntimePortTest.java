package net.linlan.dsh.domain.typert.adapter.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("T2.029 IPtcRuntimePort 端口契约")
class IPtcRuntimePortTest {

    @Test
    @DisplayName("execute(script, env) 返回执行输出")
    void executeReturnsOutput() {
        IPtcRuntimePort port = new StubPort("ptc-output");
        String result = port.execute("console.log(1)", Map.of());
        assertEquals("ptc-output", result);
    }

    @Test
    @DisplayName("execute 接受 null 环境变量")
    void executeAcceptsNullEnv() {
        IPtcRuntimePort port = new StubPort("ok");
        String result = port.execute("script", null);
        assertEquals("ok", result);
    }

    @Test
    @DisplayName("execute 接受非空环境变量映射")
    void executeAcceptsEnvMap() {
        IPtcRuntimePort port = new StubPort("with-env");
        String result = port.execute("script", Map.of("FOO", "bar", "NUM", 42));
        assertEquals("with-env", result);
    }

    @Test
    @DisplayName("实现类实例是 IPtcRuntimePort 子类型")
    void implementationIsPortSubtype() {
        IPtcRuntimePort port = new StubPort("");
        assertNotNull(port);
        assert IPtcRuntimePort.class.isInstance(port);
    }

    static final class StubPort implements IPtcRuntimePort {
        private final String output;

        StubPort(String output) {
            this.output = output;
        }

        @Override
        public String execute(String script, Map<String, Object> env) {
            return output;
        }
    }
}
