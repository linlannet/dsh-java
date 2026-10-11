package net.linlan.dsh.cases.runtime.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.api.dto.ActivateModelSettingRequestDTO;
import net.linlan.dsh.api.dto.ModelSettingResponseDTO;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.domain.runtime.setting.IModelSettingCommandService;
import net.linlan.dsh.domain.runtime.setting.model.valobj.ModelSettingVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 模型设置激活节点。
 * <p>流程：校验 channelCode → 调用领域命令服务将该渠道置为当前生效 → 映射为配置 DTO。</p>
 * <p>数据示例：传入 {@code channelCode=deepseek-glm}；
 * 返回该配置的 DTO 且 active=true，其他渠道自动取消激活。</p>
 */
@Service
public class ModelSettingActivateNode
        extends AbstractStrategyRouter<ActivateModelSettingRequestDTO, NoContext, ModelSettingResponseDTO> {

    private static final Logger log = LoggerFactory.getLogger(ModelSettingActivateNode.class);

    private final IModelSettingCommandService modelSettingCommandService;

    public ModelSettingActivateNode(IModelSettingCommandService modelSettingCommandService) {
        this.modelSettingCommandService = modelSettingCommandService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected ModelSettingResponseDTO doApply(ActivateModelSettingRequestDTO command, NoContext context) {
        CaseGuard.requireNonNull(command, "activate model setting command must not be null");
        CaseGuard.requireText(command.channelCode(), "channelCode must not be blank");
        ModelSettingVO setting = modelSettingCommandService.activate(command.channelCode());
        log.info("[Runtime] 模型渠道激活完成，channelCode={} modelCode={}", setting.channelCode(), setting.modelCode());
        return new ModelSettingResponseDTO(
                setting.channelCode(), setting.displayName(), setting.providerCode(), setting.modelCode(),
                setting.baseUrl(), setting.apiKeyRef(), setting.protocol(),
                setting.enabled(), setting.active(), setting.updatedAt()
        );
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<ActivateModelSettingRequestDTO, NoContext, ModelSettingResponseDTO> getNext(
            ActivateModelSettingRequestDTO command, NoContext context) {
        return defaultStrategyHandler;
    }
}
