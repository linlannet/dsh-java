package net.linlan.dsh.domain.typert.model.valobj;

/**
 * Result of a Remote method call: either ok with a value, or error with a failure.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public sealed interface RemoteResult permits RemoteResult.Ok, RemoteResult.Error {

    /**
     * Ok 定义当前模块中的record契约与职责。
     */
    record Ok(Object value) implements RemoteResult {}

    /**
     * Error 定义当前模块中的record契约与职责。
     */
    record Error(RemoteFailure failure) implements RemoteResult {}

    static RemoteResult ok(Object value) { return new Ok(value); }
    static RemoteResult error(RemoteFailure failure) { return new Error(failure); }
}
