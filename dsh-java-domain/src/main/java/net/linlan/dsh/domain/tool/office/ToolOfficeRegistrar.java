package net.linlan.dsh.domain.tool.office;

import net.linlan.dsh.domain.document.office.model.valobj.OfficeKitConfig;
import net.linlan.dsh.domain.skill.model.entity.SkillDefinition;
import net.linlan.dsh.domain.skill.office.OfficeSkill;
import net.linlan.dsh.domain.storage.conversion.adapter.port.IOfficeConverterPort;
import net.linlan.dsh.domain.tool.adapter.ToolRegistry;

/** 仅在 Office 单技能成功加载后激活 Office 工具。 */
public final class ToolOfficeRegistrar {

    private final IOfficeConverterPort converter;
    private final OfficeKitConfig config;

    public ToolOfficeRegistrar(IOfficeConverterPort converter, OfficeKitConfig config) {
        this.converter = converter;
        this.config = config == null ? OfficeKitConfig.defaults() : config;
    }

    /**
     * 注册三个 Office 工具；重复激活保持幂等。
     *
     * @return 当前技能已激活 Office 工具时返回 true
     */
    public boolean register(ToolRegistry registry, SkillDefinition skill) {
        if (registry == null || converter == null || skill == null
                || !OfficeSkill.NAME.equals(skill.name())) {
            return false;
        }
        registerIfAbsent(registry, new OfficeRenderTool(converter, config));
        registerIfAbsent(registry, new OfficeConvertTool(converter, config));
        registerIfAbsent(registry, new OfficeRecalculateTool(converter, config));
        return true;
    }

    private static void registerIfAbsent(ToolRegistry registry,
                                         net.linlan.dsh.domain.model.entity.ToolDefinition tool) {
        if (registry.lookup(tool.name()).isEmpty()) {
            registry.register(tool);
        }
    }
}
