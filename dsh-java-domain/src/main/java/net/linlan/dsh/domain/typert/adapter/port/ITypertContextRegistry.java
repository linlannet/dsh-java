package net.linlan.dsh.domain.typert.adapter.port;

import java.util.Optional;

/**
 * Runtime registry for Host Context resolvers and Client Context binders.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ITypertContextRegistry {

    void registerHost(String key, Object resolver);

    Optional<Object> getHost(String key);

    void registerClient(String key, Object binder);

    Optional<Object> getClient(String key);
}
