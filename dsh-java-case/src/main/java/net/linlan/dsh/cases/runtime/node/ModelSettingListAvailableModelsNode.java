package net.linlan.dsh.cases.runtime.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.api.dto.AvailableModelResponseDTO;
import net.linlan.dsh.domain.runtime.setting.IModelSettingQueryService;
import net.linlan.dsh.domain.runtime.setting.model.valobj.ModelSettingVO;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 可用模型渠道查询节点。
 * <p>流程：读取全部配置 → 过滤启用且模型编码非空的记录 → 返回渠道列表。</p>
 */
@Service
public class ModelSettingListAvailableModelsNode
        extends AbstractStrategyRouter<Void, NoContext, List<AvailableModelResponseDTO>> {

    private static final Logger log = LoggerFactory.getLogger(ModelSettingListAvailableModelsNode.class);

    private final IModelSettingQueryService modelSettingQueryService;

    public ModelSettingListAvailableModelsNode(IModelSettingQueryService modelSettingQueryService) {
        this.modelSettingQueryService = modelSettingQueryService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected List<AvailableModelResponseDTO> doApply(Void request, NoContext context) {
        var result = modelSettingQueryService.queryAll().stream()
                .filter(ModelSettingVO::enabled)
                .filter(setting -> setting.modelCode() != null && !setting.modelCode().isBlank())
                .map(setting -> new AvailableModelResponseDTO(
                        setting.channelCode(), setting.displayName(), setting.providerCode(), setting.modelCode()
                ))
                .toList();
        log.debug("[Runtime] 可用模型查询完成，count={}", result.size());
        return result;
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<Void, NoContext, List<AvailableModelResponseDTO>> getNext(Void request, NoContext context) {
        return defaultStrategyHandler;
    }
}
