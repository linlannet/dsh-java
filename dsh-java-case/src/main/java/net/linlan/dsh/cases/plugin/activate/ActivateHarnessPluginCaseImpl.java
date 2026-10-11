package net.linlan.dsh.cases.plugin.activate;

import net.linlan.dsh.api.dto.ActivatePluginRequestDTO;
import net.linlan.dsh.api.dto.ActivatePluginResponseDTO;
import net.linlan.dsh.cases.plugin.IActivateHarnessPluginCase;
import net.linlan.dsh.cases.plugin.IRunHarnessPluginCase;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeOutcome;
import net.linlan.dsh.cases.plugin.lifecycle.PluginExecutionInput;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeKind;
import net.linlan.dsh.cases.plugin.lifecycle.factory.PluginRuntimePipelineFactory;
import net.linlan.dsh.domain.plugin.IPluginRegistryService;
import org.springframework.stereotype.Service;

/**
 * 协调插件激活纵切面。
 *
 * <p>调用流程：Controller/API → 插件用例 → 领域插件服务 → PluginRuntime/Bridge。</p>
 * <p>示例：安装插件后先激活，再通过统一工具名调用插件工具。</p>
 *
 */
@Service
public class ActivateHarnessPluginCaseImpl implements IActivateHarnessPluginCase {

    private final PluginRuntimePipelineFactory pipelineFactory;
    private final IRunHarnessPluginCase runHarnessPluginCase;
    private final IPluginRegistryService pluginRegistryService;

    /**
     * 激活组件，准备其运行时能力。
     */
    public ActivateHarnessPluginCaseImpl(
            PluginRuntimePipelineFactory pipelineFactory,
            IRunHarnessPluginCase runHarnessPluginCase,
            IPluginRegistryService pluginRegistryService
    ) {
        this.pipelineFactory = pipelineFactory;
        this.runHarnessPluginCase = runHarnessPluginCase;
        this.pluginRegistryService = pluginRegistryService;
    }

    /**
     * 用例执行入口，组装 Pipeline 并返回处理结果。
     */
    @Override
    public ActivatePluginResponseDTO execute(ActivatePluginRequestDTO request) {
        PluginRuntimeOutcome outcome = pipelineFactory.pluginPipeline().execute(
                new PluginExecutionInput(request.pluginId(), PluginRuntimeKind.ACTIVATE));
        if (outcome instanceof PluginRuntimeOutcome.Activation activation) {
            try {
                var runResult = runHarnessPluginCase.execute(
                        new net.linlan.dsh.api.dto.RunPluginRequestDTO(request.pluginId()));
                if (!runResult.running()) {
                    markFailed(request.pluginId());
                    throw new IllegalStateException(
                            "plugin activated but failed to start: " + runResult.message());
                }
            } catch (RuntimeException exception) {
                markFailed(request.pluginId());
                throw exception;
            }
            return activation.result();
        }
        throw new IllegalStateException("unexpected plugin activation outcome");
    }

    private void markFailed(String pluginId) {
        try {
            pluginRegistryService.refreshStatus(pluginRegistryService.loadPlugin(pluginId), false);
        } catch (RuntimeException statusException) {
            statusException.addSuppressed(new IllegalStateException(
                    "failed to persist plugin failure status: " + pluginId));
        }
    }
}
