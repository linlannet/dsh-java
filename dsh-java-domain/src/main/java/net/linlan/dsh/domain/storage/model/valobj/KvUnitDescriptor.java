package net.linlan.dsh.domain.storage.model.valobj;

import java.util.Set;

/**
 * Describes a KV unit: a named logical store with typed tables.
 *
 * @param name   unit name (POSIX identifier)
 * @param tables table names within the unit
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record KvUnitDescriptor(String name, Set<String> tables) {
    public KvUnitDescriptor {
        tables = tables == null ? Set.of() : Set.copyOf(tables);
    }
}
