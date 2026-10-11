package net.linlan.dsh.domain.storage.model.valobj;

import java.util.Map;

/**
 * Immutable snapshot of all tables in a KV unit at load time.
 *
 * @param tables table name → (key → value)
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record KvSnapshot(Map<String, Map<String, String>> tables) {
    public KvSnapshot {
        tables = tables == null ? Map.of() : Map.copyOf(tables);
    }
}
