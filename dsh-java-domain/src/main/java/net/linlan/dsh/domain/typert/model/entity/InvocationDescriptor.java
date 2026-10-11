package net.linlan.dsh.domain.typert.model.entity;

import java.util.Map;

/**
 * InvocationDescriptor 表示InvocationDescriptor。
 */
public record InvocationDescriptor(
        String namespace,
        String method,
        Map<String, Object> paramsSchema,
        Map<String, Object> resultSchema,
        String description
) {
    /**
     * 处理endpoint。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public String endpoint() {
        return namespace + "/" + method;
    }
}
