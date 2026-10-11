package net.linlan.dsh.cases.runtime.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.api.dto.ChannelPresetResponseDTO;
import net.linlan.dsh.domain.channel.IChannelPresetQueryService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 渠道模板列表节点。
 * <p>流程：接收空请求 → 调用领域查询服务读取渠道模板 → 转换为接口 DTO 列表。</p>
 * <p>数据示例：无入参；返回内置渠道模板（协议、默认地址、推荐模型）。</p>
 *
 *
 */
@Service
public class ChannelPresetListNode
        extends AbstractStrategyRouter<Void, NoContext, List<ChannelPresetResponseDTO>> {

    private static final Logger log = LoggerFactory.getLogger(ChannelPresetListNode.class);

    private final IChannelPresetQueryService channelPresetQueryService;

    public ChannelPresetListNode(IChannelPresetQueryService channelPresetQueryService) {
        this.channelPresetQueryService = channelPresetQueryService;
    }

    /**
     * 执行当前节点核心逻辑：读取渠道模板注册表并映射为 DTO。
     */
    @Override
    protected List<ChannelPresetResponseDTO> doApply(Void request, NoContext context) {
        var result = channelPresetQueryService.listPresets().stream()
                .map(p -> new ChannelPresetResponseDTO(
                        p.id(),
                        p.displayName(),
                        p.protocol(),
                        p.baseUrl(),
                        p.authScheme(),
                        p.modelSuggestions(),
                        p.regionGroup()
                ))
                .toList();
        log.debug("[Runtime] 渠道模板查询完成，count={}", result.size());
        return result;
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<Void, NoContext, List<ChannelPresetResponseDTO>> getNext(Void request, NoContext context) {
        return defaultStrategyHandler;
    }
}
