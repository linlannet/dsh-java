package net.linlan.dsh.domain.lsp.model.valobj;

import java.util.List;
import java.util.Optional;

/**
 * Result of one LSP query.
 *
 * @param locations  locations (for definition/references/implementation)
 * @param hoverText  hover text (for hover queries)
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record LspQueryResult(List<LspLocation> locations, Optional<String> hoverText) {
    /**
     * 创建 Locations。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param locations locations参数。
     * @return 创建的领域对象。
     */
    public static LspQueryResult ofLocations(List<LspLocation> locations) {
        return new LspQueryResult(locations, Optional.empty());
    }
    /**
     * 创建 Hover。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param text text参数。
     * @return 创建的领域对象。
     */
    public static LspQueryResult ofHover(String text) {
        return new LspQueryResult(List.of(), Optional.of(text));
    }
}
