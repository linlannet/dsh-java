package net.linlan.dsh.domain.typert.ptc.python;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PythonBindingRegistry 管理 PTC Python 运行时的 binding 注册机制。
 */
public final class PythonBindingRegistry {

    private final Map<String, PythonBinding> bindings = new ConcurrentHashMap<>();
    private final Set<String> blockedModules;

    public PythonBindingRegistry() {
        this(Set.of("os", "subprocess", "socket", "ctypes", "multiprocessing"));
    }

    public PythonBindingRegistry(Set<String> blockedModules) {
        this.blockedModules = blockedModules == null ? Set.of() : Set.copyOf(blockedModules);
    }

    public void register(PythonBinding binding) {
        Objects.requireNonNull(binding, "binding 不能为空");
        bindings.put(binding.name(), binding);
    }

    public PythonBinding get(String name) {
        return bindings.get(name);
    }

    public Collection<PythonBinding> list() {
        return bindings.values();
    }

    public boolean remove(String name) {
        return bindings.remove(name) != null;
    }

    public boolean exists(String name) {
        return bindings.containsKey(name);
    }

    public int size() {
        return bindings.size();
    }

    public boolean isModuleBlocked(String moduleName) {
        return blockedModules.contains(moduleName);
    }

    public Set<String> blockedModules() {
        return blockedModules;
    }

    public SecurityCheckResult checkScriptSecurity(String pythonCode) {
        if (pythonCode == null || pythonCode.isBlank()) {
            return new SecurityCheckResult(false, "script is empty");
        }
        for (String module : blockedModules) {
            if (pythonCode.contains("import " + module) || pythonCode.contains("from " + module)) {
                return new SecurityCheckResult(false, "blocked module: " + module);
            }
        }
        if (pythonCode.contains("__import__")) {
            return new SecurityCheckResult(false, "dynamic import via __import__ is blocked");
        }
        if (pythonCode.contains("exec(") || pythonCode.contains("eval(")) {
            return new SecurityCheckResult(false, "exec/eval are blocked");
        }
        return new SecurityCheckResult(true, null);
    }

    public record SecurityCheckResult(boolean passed, String reason) {}
}
