package net.linlan.dsh.domain.llm.extension.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LlmRequestExtensionRegistry 请求扩展字段注册")
class LlmRequestExtensionRegistryTest {

    @Test
    @DisplayName("注册字段后 resolve 返回该字段值")
    void registerAndResolve() {
        var registry = new LlmRequestExtensionRegistry();
        registry.register("thinking_budget", ctx -> 4096);

        var result = registry.resolve(Map.of());
        assertEquals(1, result.size());
        assertEquals(4096, result.get("thinking_budget"));
    }

    @Test
    @DisplayName("注册多个字段 resolve 返回全部")
    void registerMultipleFields() {
        var registry = new LlmRequestExtensionRegistry();
        registry.register("field1", ctx -> "value1");
        registry.register("field2", ctx -> 42);
        registry.register("field3", ctx -> true);

        var result = registry.resolve(Map.of());
        assertEquals(3, result.size());
        assertEquals("value1", result.get("field1"));
        assertEquals(42, result.get("field2"));
        assertEquals(true, result.get("field3"));
    }

    @Test
    @DisplayName("provider 返回 null 时字段不包含在结果中")
    void nullValueExcluded() {
        var registry = new LlmRequestExtensionRegistry();
        registry.register("nullField", ctx -> null);
        registry.register("validField", ctx -> "valid");

        var result = registry.resolve(Map.of());
        assertEquals(1, result.size());
        assertFalse(result.containsKey("nullField"));
        assertEquals("valid", result.get("validField"));
    }

    @Test
    @DisplayName("provider 抛异常时字段被跳过")
    void exceptionProviderSkipped() {
        var registry = new LlmRequestExtensionRegistry();
        registry.register("errorField", ctx -> { throw new RuntimeException("boom"); });
        registry.register("validField", ctx -> "valid");

        var result = registry.resolve(Map.of());
        assertEquals(1, result.size());
        assertEquals("valid", result.get("validField"));
    }

    @Test
    @DisplayName("注销字段后 resolve 不包含该字段")
    void unregisterExcludesField() {
        var registry = new LlmRequestExtensionRegistry();
        registry.register("field1", ctx -> "value1");
        registry.unregister("field1");

        var result = registry.resolve(Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("空字段名抛异常")
    void emptyFieldNameRejected() {
        var registry = new LlmRequestExtensionRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register("", ctx -> null));
        assertThrows(IllegalArgumentException.class, () -> registry.register(null, ctx -> null));
    }

    @Test
    @DisplayName("null provider 抛异常")
    void nullProviderRejected() {
        var registry = new LlmRequestExtensionRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register("field", null));
    }

    @Test
    @DisplayName("isRegistered 正确判断")
    void isRegisteredCorrect() {
        var registry = new LlmRequestExtensionRegistry();
        assertFalse(registry.isRegistered("field"));
        registry.register("field", ctx -> "value");
        assertTrue(registry.isRegistered("field"));
    }

    @Test
    @DisplayName("size 返回已注册字段数")
    void sizeCorrect() {
        var registry = new LlmRequestExtensionRegistry();
        assertEquals(0, registry.size());
        registry.register("a", ctx -> 1);
        registry.register("b", ctx -> 2);
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("resolve null 上下文返回已注册字段")
    void nullContextHandled() {
        var registry = new LlmRequestExtensionRegistry();
        registry.register("field", ctx -> "value");
        var result = registry.resolve(null);
        assertEquals("value", result.get("field"));
    }
}
