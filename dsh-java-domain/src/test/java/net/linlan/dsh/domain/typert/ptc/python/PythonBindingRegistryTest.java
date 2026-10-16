package net.linlan.dsh.domain.typert.ptc.python;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PythonBindingRegistry Python 绑定注册与安全")
class PythonBindingRegistryTest {

    @Test
    @DisplayName("register 和 get 正常工作")
    void registerAndGet() {
        var registry = new PythonBindingRegistry();
        registry.register(new PythonBinding("math", "/bindings/math.py", "Math binding"));
        assertNotNull(registry.get("math"));
        assertEquals("/bindings/math.py", registry.get("math").scriptPath());
        assertEquals("Math binding", registry.get("math").description());
    }

    @Test
    @DisplayName("exists 检查绑定是否存在")
    void existsChecksBinding() {
        var registry = new PythonBindingRegistry();
        registry.register(new PythonBinding("math", "/math.py", ""));
        assertTrue(registry.exists("math"));
        assertFalse(registry.exists("nonexistent"));
    }

    @Test
    @DisplayName("remove 删除绑定")
    void removeBinding() {
        var registry = new PythonBindingRegistry();
        registry.register(new PythonBinding("math", "/math.py", ""));
        assertTrue(registry.remove("math"));
        assertFalse(registry.exists("math"));
    }

    @Test
    @DisplayName("remove 不存在的绑定返回 false")
    void removeNonExistentReturnsFalse() {
        var registry = new PythonBindingRegistry();
        assertFalse(registry.remove("nonexistent"));
    }

    @Test
    @DisplayName("list 返回所有绑定")
    void listReturnsAll() {
        var registry = new PythonBindingRegistry();
        registry.register(new PythonBinding("a", "/a.py", ""));
        registry.register(new PythonBinding("b", "/b.py", ""));
        assertEquals(2, registry.list().size());
    }

    @Test
    @DisplayName("默认阻止危险模块")
    void defaultBlockedModules() {
        var registry = new PythonBindingRegistry();
        assertTrue(registry.isModuleBlocked("os"));
        assertTrue(registry.isModuleBlocked("subprocess"));
        assertTrue(registry.isModuleBlocked("socket"));
        assertTrue(registry.isModuleBlocked("ctypes"));
        assertFalse(registry.isModuleBlocked("math"));
    }

    @Test
    @DisplayName("自定义阻止模块")
    void customBlockedModules() {
        var registry = new PythonBindingRegistry(Set.of("os", "sys"));
        assertTrue(registry.isModuleBlocked("os"));
        assertTrue(registry.isModuleBlocked("sys"));
        assertFalse(registry.isModuleBlocked("subprocess"));
    }

    @Test
    @DisplayName("checkScriptSecurity 拦截 import os")
    void checkScriptSecurityBlocksImportOs() {
        var registry = new PythonBindingRegistry();
        var result = registry.checkScriptSecurity("import os\nos.system('rm -rf /')");
        assertFalse(result.passed());
        assertTrue(result.reason().contains("os"));
    }

    @Test
    @DisplayName("checkScriptSecurity 拦截 from subprocess import")
    void checkScriptSecurityBlocksFromSubprocess() {
        var registry = new PythonBindingRegistry();
        var result = registry.checkScriptSecurity("from subprocess import Popen\nPopen(['ls'])");
        assertFalse(result.passed());
        assertTrue(result.reason().contains("subprocess"));
    }

    @Test
    @DisplayName("checkScriptSecurity 拦截 __import__")
    void checkScriptSecurityBlocksDynamicImport() {
        var registry = new PythonBindingRegistry();
        var result = registry.checkScriptSecurity("__import__('os').system('ls')");
        assertFalse(result.passed());
        assertTrue(result.reason().contains("__import__"));
    }

    @Test
    @DisplayName("checkScriptSecurity 拦截 exec/eval")
    void checkScriptSecurityBlocksExecEval() {
        var registry = new PythonBindingRegistry();
        assertFalse(registry.checkScriptSecurity("exec('malicious')").passed());
        assertFalse(registry.checkScriptSecurity("eval('1+1')").passed());
    }

    @Test
    @DisplayName("checkScriptSecurity 通过安全脚本")
    void checkScriptSecurityPassesSafeScript() {
        var registry = new PythonBindingRegistry();
        var result = registry.checkScriptSecurity("print('hello world')");
        assertTrue(result.passed());
    }

    @Test
    @DisplayName("checkScriptSecurity 拒绝空脚本")
    void checkScriptSecurityRejectsEmpty() {
        var registry = new PythonBindingRegistry();
        assertFalse(registry.checkScriptSecurity("").passed());
        assertFalse(registry.checkScriptSecurity(null).passed());
    }

    @Test
    @DisplayName("register 覆盖同名绑定")
    void registerOverwritesSameName() {
        var registry = new PythonBindingRegistry();
        registry.register(new PythonBinding("math", "/v1/math.py", "v1"));
        registry.register(new PythonBinding("math", "/v2/math.py", "v2"));
        assertEquals("/v2/math.py", registry.get("math").scriptPath());
        assertEquals(1, registry.size());
    }
}

@DisplayName("PythonBinding 值对象")
class PythonBindingTest {

    @Test
    @DisplayName("正常构造")
    void normalConstruction() {
        var binding = new PythonBinding("math", "/math.py", "Math binding");
        assertEquals("math", binding.name());
        assertEquals("/math.py", binding.scriptPath());
        assertEquals("Math binding", binding.description());
    }

    @Test
    @DisplayName("null description 默认为空字符串")
    void nullDescriptionDefaultsToEmpty() {
        var binding = new PythonBinding("math", "/math.py", null);
        assertEquals("", binding.description());
    }

    @Test
    @DisplayName("null name 抛异常")
    void nullNameThrows() {
        assertThrows(NullPointerException.class, () -> new PythonBinding(null, "/math.py", ""));
    }

    @Test
    @DisplayName("空白 name 抛异常")
    void blankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new PythonBinding("", "/math.py", ""));
        assertThrows(IllegalArgumentException.class, () -> new PythonBinding("  ", "/math.py", ""));
    }

    @Test
    @DisplayName("null scriptPath 抛异常")
    void nullScriptPathThrows() {
        assertThrows(NullPointerException.class, () -> new PythonBinding("math", null, ""));
    }

    @Test
    @DisplayName("空白 scriptPath 抛异常")
    void blankScriptPathThrows() {
        assertThrows(IllegalArgumentException.class, () -> new PythonBinding("math", "", ""));
    }
}
