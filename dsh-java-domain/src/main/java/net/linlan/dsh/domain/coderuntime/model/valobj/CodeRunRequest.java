package net.linlan.dsh.domain.coderuntime.model.valobj;

import java.util.Map;

/**
 * CodeRunRequest 表示CodeRunRequest请求。
 */
public record CodeRunRequest(String code, String language, Map<String, CodeBinding> bindings) {
    public static final String JS = "js";
    public static final String PYTHON = "python";

    /**
     * CodeBinding 定义当前模块中的record契约与职责。
     */
    public record CodeBinding(String name, boolean isFunction, String returnType) {}
}
