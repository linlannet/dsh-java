package net.linlan.dsh.cases.runtime;

import net.linlan.dsh.api.dto.ActivateModelSettingRequestDTO;
import net.linlan.dsh.api.dto.DeleteModelSettingRequestDTO;
import net.linlan.dsh.api.dto.SaveModelSettingRequestDTO;
import net.linlan.dsh.api.dto.ModelSettingResponseDTO;
import net.linlan.dsh.cases.runtime.factory.RuntimePipelineFactory;
import org.springframework.stereotype.Service;

/**
 * 模型设置写操作编排实现。
 * <p>流程：将保存/删除/激活请求交给对应 Pipeline → 执行领域命令节点 → 保存与激活返回 DTO，删除无返回。</p>
 * <p>数据示例：保存 {@code channelCode=default} 后返回 {@code enabled=true}；
 * 激活 {@code channelCode=default} 后该渠道成为当前生效配置。</p>
 */
@Service
public class ModelSettingCommandCaseImpl implements IModelSettingCommandCase {
    private final RuntimePipelineFactory runtimePipelineFactory;

    public ModelSettingCommandCaseImpl(RuntimePipelineFactory runtimePipelineFactory) {
        this.runtimePipelineFactory = runtimePipelineFactory;
    }

    /**
     * 编排保存操作，将命令交给对应 Pipeline 执行。
     */
    @Override
    public ModelSettingResponseDTO save(SaveModelSettingRequestDTO command) {
        return runtimePipelineFactory.savePipeline().execute(command);
    }

    /**
     * 编排删除操作，将删除命令交给对应 Pipeline 执行。
     */
    @Override
    public void delete(DeleteModelSettingRequestDTO command) {
        runtimePipelineFactory.deletePipeline().execute(command);
    }

    /**
     * 编排激活操作，将切换命令交给对应 Pipeline 执行。
     */
    @Override
    public ModelSettingResponseDTO activate(ActivateModelSettingRequestDTO command) {
        return runtimePipelineFactory.activatePipeline().execute(command);
    }
}
