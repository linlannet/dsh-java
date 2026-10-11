package net.linlan.dsh.cases.plugin.lifecycle;

import net.linlan.dsh.api.dto.ActivatePluginResponseDTO;
import net.linlan.dsh.api.dto.RunPluginResponseDTO;

/**
 * 插件运行期链路的多态结果。
 * <p>流程：激活链路返回 {@link Activation}，运行链路返回 {@link Run}；Trigger 层负责映射成对应 API DTO。</p>
 * <p>数据示例：{@code Activation(result.status=ENABLED)} 或 {@code Run(result.running=true, processId=8102)}。</p>
 */
public sealed interface PluginRuntimeOutcome {

    record Activation(ActivatePluginResponseDTO result) implements PluginRuntimeOutcome {
    }

    record Run(RunPluginResponseDTO result) implements PluginRuntimeOutcome {
    }
}
