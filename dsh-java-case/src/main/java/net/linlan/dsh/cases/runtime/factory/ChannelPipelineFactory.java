package net.linlan.dsh.cases.runtime.factory;

import net.linlan.dsh.cases.orchestration.CasePipeline;
import net.linlan.dsh.cases.orchestration.CasePipelineFactory;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.api.dto.ChannelPresetResponseDTO;
import net.linlan.dsh.api.dto.DiscoverModelsRequestDTO;
import net.linlan.dsh.cases.runtime.node.ChannelPresetListNode;
import net.linlan.dsh.cases.runtime.node.ModelDiscoverySyncNode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 渠道 Pipeline 工厂。
 * <p>流程：按查询目标组装 Pipeline，分别路由到渠道模板列表节点和上游模型同步节点。</p>
 * <p>数据示例：模板列表无入参返回全部渠道模板；模型同步传入 baseUrl/protocol 返回模型编码列表。</p>
 *
 *
 */
@Service
public class ChannelPipelineFactory
        implements CasePipelineFactory<Void, NoContext, List<ChannelPresetResponseDTO>> {

    private final ChannelPresetListNode channelPresetListNode;
    private final ModelDiscoverySyncNode modelDiscoverySyncNode;

    public ChannelPipelineFactory(
            ChannelPresetListNode channelPresetListNode,
            ModelDiscoverySyncNode modelDiscoverySyncNode
    ) {
        this.channelPresetListNode = channelPresetListNode;
        this.modelDiscoverySyncNode = modelDiscoverySyncNode;
    }

    /**
     * 构建渠道模板列表 Pipeline。
     */
    public CasePipeline<Void, NoContext, List<ChannelPresetResponseDTO>> listPresetsPipeline() {
        return CasePipeline.of("List channel presets", channelPresetListNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建上游模型同步 Pipeline。
     */
    public CasePipeline<DiscoverModelsRequestDTO, NoContext, List<String>> syncModelsPipeline() {
        return CasePipeline.of("Sync upstream models", modelDiscoverySyncNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建默认执行 Pipeline，绑定渠道模板列表节点。
     */
    @Override
    public CasePipeline<Void, NoContext, List<ChannelPresetResponseDTO>> pipeline() {
        return listPresetsPipeline();
    }
}
