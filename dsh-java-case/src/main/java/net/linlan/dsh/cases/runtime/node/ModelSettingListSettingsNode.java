package net.linlan.dsh.cases.runtime.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.api.dto.ModelSettingResponseDTO;
import net.linlan.dsh.domain.runtime.setting.IModelSettingQueryService;
import net.linlan.dsh.domain.runtime.setting.model.valobj.ModelSettingVO;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 模型配置列表节点。
 * <p>流程：接收空请求 → 调用领域查询服务读取全部配置 → 转换为配置 DTO 列表。</p>
 * <p>数据示例：无入参；返回 {@code [{channelCode: "default", baseUrl: "http://127.0.0.1:28080/v1", enabled: true}]}。</p>
 */
@Service
public class ModelSettingListSettingsNode
        extends AbstractStrategyRouter<Void, NoContext, List<ModelSettingResponseDTO>> {

    private static final Logger log = LoggerFactory.getLogger(ModelSettingListSettingsNode.class);

    private final IModelSettingQueryService modelSettingQueryService;

    public ModelSettingListSettingsNode(IModelSettingQueryService modelSettingQueryService) {
        this.modelSettingQueryService = modelSettingQueryService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected List<ModelSettingResponseDTO> doApply(Void request, NoContext context) {
        var result = modelSettingQueryService.queryAll().stream().map(this::toResponse).toList();
        log.debug("[Runtime] 模型配置查询完成，count={}", result.size());
        return result;
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<Void, NoContext, List<ModelSettingResponseDTO>> getNext(Void request, NoContext context) {
        return defaultStrategyHandler;
    }

    /**
     * 将领域层结果对象转换为用例层 DTO。
     */
    private ModelSettingResponseDTO toResponse(ModelSettingVO setting) {
        return new ModelSettingResponseDTO(
                setting.channelCode(), setting.displayName(), setting.providerCode(), setting.modelCode(),
                setting.baseUrl(), setting.apiKeyRef(), setting.protocol(),
                setting.enabled(), setting.active(), setting.updatedAt()
        );
    }
}
