package net.linlan.dsh.domain.runtime.tool.model.valobj;

/**
 * ToolCatalogEntryVO 表示ToolCatalogEntry的领域值对象。
 */
public record ToolCatalogEntryVO(
        String toolCode,
        String displayName,
        String description,
        String category,
        boolean enabled,
        boolean requiresApproval
) {
}
