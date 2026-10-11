package net.linlan.dsh.cases.plugin.run;

import net.linlan.dsh.api.dto.RunPluginRequestDTO;
import net.linlan.dsh.api.dto.RunPluginResponseDTO;
import net.linlan.dsh.cases.plugin.IRunHarnessPluginCase;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeOutcome;
import net.linlan.dsh.cases.plugin.lifecycle.PluginExecutionInput;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeKind;
import net.linlan.dsh.cases.plugin.lifecycle.factory.PluginRuntimePipelineFactory;
import org.springframework.stereotype.Service;

/**
 * 协调插件进程启动。
 *
 * <p>调用流程：Controller/API → 插件用例 → 领域插件服务 → PluginRuntime/Bridge。</p>
 * <p>示例：安装插件后先激活，再通过统一工具名调用插件工具。</p>
 *
 */
@Service
public class RunHarnessPluginCaseImpl implements IRunHarnessPluginCase {

    private final PluginRuntimePipelineFactory pipelineFactory;

    /**
     * 运行Harness Plugin Case Impl。
     */
    public RunHarnessPluginCaseImpl(
            PluginRuntimePipelineFactory pipelineFactory
    ) {
        this.pipelineFactory = pipelineFactory;
    }

    /**
     * 用例执行入口，组装 Pipeline 并返回处理结果。
     */
    @Override
    public RunPluginResponseDTO execute(RunPluginRequestDTO request) {
        if (pipelineFactory.pluginPipeline().execute(new PluginExecutionInput(request.pluginId(), PluginRuntimeKind.RUN)) instanceof PluginRuntimeOutcome.Run run) {
            return run.result();
        }
        throw new IllegalStateException("unexpected plugin run outcome");
    }
}
