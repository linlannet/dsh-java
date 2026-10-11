package net.linlan.dsh.domain.runtime.tool.model.entity;

import java.util.Objects;

/**
 * Stores one persisted tool catalog entry.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public class ToolCatalogEntryEntity {

    private final String toolCode;
    private final String displayName;
    private final String description;
    private final String category;
    private final boolean requiresApproval;

    /**
     * 创建工具目录条目。
     * <p>用途：保存工具名称、描述、参数 Schema 和权限标识。</p>
     */
    public ToolCatalogEntryEntity(
            String toolCode,
            String displayName,
            String description,
            String category,
            boolean requiresApproval
    ) {
        this.toolCode = Objects.requireNonNull(toolCode, "toolCode");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.description = description;
        this.category = Objects.requireNonNull(category, "category");
        this.requiresApproval = requiresApproval;
    }

    /**
     * 获取Tool Code。
     */
    public String getToolCode() { return toolCode; }
    /**
     * 获取DisplayName名称。
     * @return 领域处理结果。
     */
    public String getDisplayName() { return displayName; }
    /**
     * 获取Description。
     */
    public String getDescription() { return description; }
    /**
     * 获取Category。
     */
    public String getCategory() { return category; }
    /**
     * 判断Requires Approval是否成立。
     */
    public boolean isRequiresApproval() { return requiresApproval; }
}
