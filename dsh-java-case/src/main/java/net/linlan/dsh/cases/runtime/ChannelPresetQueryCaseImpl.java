package net.linlan.dsh.cases.runtime;

import net.linlan.dsh.api.dto.ChannelPresetResponseDTO;
import net.linlan.dsh.cases.runtime.factory.ChannelPipelineFactory;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 渠道模板查询用例实现。
 * <p>流程：将查询交给渠道 Pipeline → 执行模板列表节点 → 返回 DTO 列表。</p>
 *
 *
 */
@Service
public class ChannelPresetQueryCaseImpl implements IChannelPresetQueryCase {

    private final ChannelPipelineFactory channelPipelineFactory;

    public ChannelPresetQueryCaseImpl(ChannelPipelineFactory channelPipelineFactory) {
        this.channelPipelineFactory = channelPipelineFactory;
    }

    /**
     * 编排渠道模板查询，执行查询 Pipeline 并返回 DTO 列表。
     */
    @Override
    public List<ChannelPresetResponseDTO> listPresets() {
        return channelPipelineFactory.listPresetsPipeline().execute(null);
    }
}
