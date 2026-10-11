package net.linlan.dsh.cases.plugin.install.factory;

import net.linlan.dsh.cases.orchestration.CasePipeline;
import net.linlan.dsh.cases.orchestration.CasePipelineFactory;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.api.dto.InstallPluginRequestDTO;
import net.linlan.dsh.api.dto.InstallPluginResponseDTO;
import net.linlan.dsh.cases.plugin.install.node.InstallPluginNode;
import org.springframework.stereotype.Service;

/**
 * 插件安装 Pipeline 工厂。
 * <p>流程：组装无共享上下文的安装 Pipeline → 执行 InstallPluginNode → 返回安装结果。</p>
 * <p>数据示例：传入 {@code pluginId=weather-forecast, sourcePath=/plugins/weather}；
 * 返回 {@code status=INSTALLED, directlyExecutable=true}。</p>
 */
@Service
public class PluginInstallPipelineFactory
        implements CasePipelineFactory<InstallPluginRequestDTO, NoContext, InstallPluginResponseDTO> {

    private final InstallPluginNode installPluginNode;

    public PluginInstallPipelineFactory(InstallPluginNode installPluginNode) {
        this.installPluginNode = installPluginNode;
    }

    /**
     * 构建默认执行 Pipeline，绑定首节点和上下文。
     */
    @Override
    public CasePipeline<InstallPluginRequestDTO, NoContext, InstallPluginResponseDTO> pipeline() {
        return CasePipeline.of("Install plugin", installPluginNode, () -> NoContext.INSTANCE);
    }
}
