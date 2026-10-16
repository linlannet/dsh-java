package net.linlan.dsh.domain.typert.ptc.python;

import java.util.Objects;

/**
 * PythonBinding 表示一个已注册的 Python 绑定（名称 → 脚本路径）。
 */
public record PythonBinding(String name, String scriptPath, String description) {
    public PythonBinding {
        Objects.requireNonNull(name, "name 不能为空");
        Objects.requireNonNull(scriptPath, "scriptPath 不能为空");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空白");
        }
        if (scriptPath.isBlank()) {
            throw new IllegalArgumentException("scriptPath 不能为空白");
        }
        description = description == null ? "" : description;
    }
}
