package net.linlan.dsh.cases.runtime.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.api.dto.ModelSettingResponseDTO;
import net.linlan.dsh.api.dto.SaveModelSettingRequestDTO;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.domain.runtime.setting.IModelSettingCommandService;
import net.linlan.dsh.domain.runtime.setting.model.valobj.ModelSettingVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 模型设置保存节点。
 * <p>流程：校验命令和 channelCode → 调用领域命令服务保存为 ModelSettingVO → 映射为配置 DTO。</p>
 * <p>数据示例：传入 {@code channelCode=default, modelCode=Deepseek-V4.1-Flash, enabled=true}；
 * 返回包含同一 channelCode 和 updatedAt 的 DTO。</p>
 */
@Service
public class ModelSettingSaveNode
        extends AbstractStrategyRouter<SaveModelSettingRequestDTO, NoContext, ModelSettingResponseDTO> {

    private static final Logger log = LoggerFactory.getLogger(ModelSettingSaveNode.class);

    private final IModelSettingCommandService modelSettingCommandService;

    public ModelSettingSaveNode(IModelSettingCommandService modelSettingCommandService) {
        this.modelSettingCommandService = modelSettingCommandService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected ModelSettingResponseDTO doApply(SaveModelSettingRequestDTO command, NoContext context) {
        CaseGuard.requireNonNull(command, "model setting command must not be null");
        CaseGuard.requireText(command.displayName(), "displayName must not be blank");
        String channelCode = command.channelCode() == null || command.channelCode().isBlank()
                ? "channel-" + java.util.UUID.randomUUID()
                : command.channelCode();
        ModelSettingVO setting = modelSettingCommandService.save(
                channelCode, command.displayName(), command.providerCode(), command.modelCode(),
                command.baseUrl(), command.apiKeyRef(), command.protocol(), command.enabled()
        );
        log.info("[Runtime] 模型渠道保存完成，channelCode={} modelCode={} protocol={} enabled={}",
                setting.channelCode(), setting.modelCode(), setting.protocol(), setting.enabled());
        return toResponse(setting);
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<SaveModelSettingRequestDTO, NoContext, ModelSettingResponseDTO> getNext(
            SaveModelSettingRequestDTO command, NoContext context) {
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
