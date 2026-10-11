package net.linlan.dsh.domain.runtime.model.model.valobj;

/**
 * Represents the model channel resolved for one execution plan.
 *
 * @param channelCode model channel identifier
 * @param providerCode provider identifier
 * @param modelCode model identifier
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record ModelProviderProfileVO(
        String channelCode,
        String providerCode,
        String modelCode
) {
}
