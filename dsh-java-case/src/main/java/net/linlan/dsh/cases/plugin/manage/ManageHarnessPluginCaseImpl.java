package net.linlan.dsh.cases.plugin.manage;

import net.linlan.dsh.api.dto.PluginLifecycleResponseDTO;
import net.linlan.dsh.cases.plugin.IManageHarnessPluginCase;
import net.linlan.dsh.cases.plugin.manage.factory.PluginManagePipelineFactory;
import net.linlan.dsh.cases.plugin.manage.node.ManagePluginNode;
import org.springframework.stereotype.Service;

/**
 * 协调插件生命周期管理纵切面。
 *
 * <p>调用流程：Controller/API → 插件用例 → 领域插件服务 → PluginRuntime/Bridge。</p>
 * <p>示例：安装插件后先激活，再通过统一工具名调用插件工具。</p>
 *
 */
@Service
public class ManageHarnessPluginCaseImpl implements IManageHarnessPluginCase {

    private final PluginManagePipelineFactory pluginManagePipelineFactory;

    /**
     * 初始化插件生命周期管理用例。
     * <p>流程：接收启用/停用/卸载命令 → 委托插件领域服务执行。</p>
     */
    public ManageHarnessPluginCaseImpl(
            PluginManagePipelineFactory pluginManagePipelineFactory
    ) {
        this.pluginManagePipelineFactory = pluginManagePipelineFactory;
    }

    /**
     * enable。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param pluginId 插件 ID。
     * @return 方法执行结果。
     */

    @Override
    public PluginLifecycleResponseDTO disable(String pluginId) {
        return execute(ManagePluginNode.Action.DISABLE, pluginId);
    }

    /**
     * uninstall。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param pluginId 插件 ID。
     * @return 方法执行结果。
     */

    @Override
    public PluginLifecycleResponseDTO enable(String pluginId) {
        return execute(ManagePluginNode.Action.ENABLE, pluginId);
    }

    /**
     * 停止目标对象。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param pluginId 插件 ID。
     * @return 方法执行结果。
     */

    @Override
    public PluginLifecycleResponseDTO uninstall(String pluginId) {
        return execute(ManagePluginNode.Action.UNINSTALL, pluginId);
    }

    /**
     * 处理当前用例步骤，完成必要校验并返回执行结果。
     */
    @Override
    public PluginLifecycleResponseDTO stop(String pluginId) {
        return execute(ManagePluginNode.Action.STOP, pluginId);
    }

    /**
     * 用例执行入口，组装 Pipeline 并返回处理结果。
     */
    private PluginLifecycleResponseDTO execute(ManagePluginNode.Action action, String pluginId) {
        return pluginManagePipelineFactory.pipeline().execute(new ManagePluginNode.Input(action, pluginId));
    }
}
