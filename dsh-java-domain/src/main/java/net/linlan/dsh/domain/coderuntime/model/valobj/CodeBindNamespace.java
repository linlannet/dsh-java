package net.linlan.dsh.domain.coderuntime.model.valobj;

import java.util.List;

/**
 * Namespace of bindings for code execution.
 *
 * @param globals top-level names exposed to the program
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record CodeBindNamespace(List<CodeRuntimeBinding> globals) {
    /**
     * CodeRuntimeBinding 定义当前模块中的class契约与职责。
     */
    public static class CodeRuntimeBinding {
        public final String name;
        public final String value;
        public final boolean isFunction;
        /**
         * 处理编码 运行时 Binding。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param name 名称。
         * @param value 参数值。
         * @param isFunction is Function参数。
         * @return 方法执行结果。
         */
        public CodeRuntimeBinding(String name, String value, boolean isFunction) {
            this.name = name;
            this.value = value;
            this.isFunction = isFunction;
        }
    }
}
