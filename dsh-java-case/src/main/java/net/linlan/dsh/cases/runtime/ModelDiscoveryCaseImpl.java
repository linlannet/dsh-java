package net.linlan.dsh.cases.runtime;

import net.linlan.dsh.api.dto.DiscoverModelsRequestDTO;
import net.linlan.dsh.cases.runtime.factory.ChannelPipelineFactory;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 上游模型同步用例实现。
 * <p>流程：将同步请求交给渠道 Pipeline → 执行模型同步节点 → 返回模型编码列表。
 * 失败沿 Pipeline 抛出，由接口层转换为错误提示。</p>
 *
 *
 */
@Service
public class ModelDiscoveryCaseImpl implements IModelDiscoveryCase {

    private final ChannelPipelineFactory channelPipelineFactory;

    public ModelDiscoveryCaseImpl(ChannelPipelineFactory channelPipelineFactory) {
        this.channelPipelineFactory = channelPipelineFactory;
    }

    /**
     * 编排上游模型同步，执行同步 Pipeline 并返回模型编码列表。
     */
    @Override
    public List<String> syncModels(String baseUrl, String apiKey, String protocol) {
        return channelPipelineFactory.syncModelsPipeline()
                .execute(new DiscoverModelsRequestDTO(baseUrl, apiKey, protocol));
    }
}
