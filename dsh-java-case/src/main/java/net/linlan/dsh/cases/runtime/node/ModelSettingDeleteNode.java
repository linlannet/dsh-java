package net.linlan.dsh.cases.runtime.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.api.dto.DeleteModelSettingRequestDTO;
import net.linlan.dsh.domain.runtime.setting.IModelSettingCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 模型设置删除节点。
 * <p>流程：校验 channelCode 非空 → 删除指定模型渠道 → Pipeline 终止。</p>
 */
@Service
public class ModelSettingDeleteNode extends AbstractStrategyRouter<DeleteModelSettingRequestDTO, NoContext, Void> {

    private static final Logger log = LoggerFactory.getLogger(ModelSettingDeleteNode.class);

    private final IModelSettingCommandService modelSettingCommandService;

    public ModelSettingDeleteNode(IModelSettingCommandService modelSettingCommandService) {
        this.modelSettingCommandService = modelSettingCommandService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected Void doApply(DeleteModelSettingRequestDTO command, NoContext context) {
        CaseGuard.requireNonNull(command, "delete model setting command must not be null");
        CaseGuard.requireText(command.channelCode(), "channelCode must not be blank");
        modelSettingCommandService.deleteByChannelCode(command.channelCode());
        log.info("[Runtime] 模型渠道删除完成，channelCode={}", command.channelCode());
        return null;
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<DeleteModelSettingRequestDTO, NoContext, Void> getNext(
            DeleteModelSettingRequestDTO command, NoContext context) {
        return defaultStrategyHandler;
    }
}
