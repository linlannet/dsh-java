package net.linlan.dsh.domain.tool.adapter;

import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionInput;
import net.linlan.dsh.domain.model.valobj.ToolExecutionMode;
import net.linlan.dsh.domain.model.valobj.ToolSchema;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * ToolRegistry 维护Tool的注册关系。
 */
public interface ToolRegistry {

    /**
     * 注册领域对象并维护索引。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param definition definition。
     * @return 领域处理结果。
     */
    Runnable register(ToolDefinition definition);

    /**
     * 处理lookup。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param name 名称。
     * @return 领域处理结果。
     */
    Optional<ToolDefinition> lookup(String name);

    /**
     * 处理all。
     * @return 领域处理结果。
     */
    Collection<ToolDefinition> all();

    /**
     * 处理executionMode。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param exec exec。
     * @return 领域处理结果。
     */
    ToolExecutionMode executionMode(ToolExecutionInput exec);

    /**
     * 处理schema列表。
     * @return 领域处理结果。
     */
    default List<ToolSchema> schemas() {
        return all().stream()
                .map(td -> new ToolSchema(td.name(), td.description(), td.parameters()))
                .toList();
    }
}
