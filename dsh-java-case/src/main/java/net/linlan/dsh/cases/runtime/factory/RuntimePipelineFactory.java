package net.linlan.dsh.cases.runtime.factory;

import net.linlan.dsh.cases.orchestration.CasePipeline;
import net.linlan.dsh.cases.orchestration.CasePipelineFactory;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.api.dto.ActivateModelSettingRequestDTO;
import net.linlan.dsh.api.dto.AvailableModelResponseDTO;
import net.linlan.dsh.api.dto.DeleteModelSettingRequestDTO;
import net.linlan.dsh.api.dto.ModelSettingResponseDTO;
import net.linlan.dsh.api.dto.SaveModelSettingRequestDTO;
import net.linlan.dsh.cases.runtime.node.ModelSettingActivateNode;
import net.linlan.dsh.cases.runtime.node.ModelSettingDeleteNode;
import net.linlan.dsh.cases.runtime.node.ModelSettingListAvailableModelsNode;
import net.linlan.dsh.cases.runtime.node.ModelSettingListSettingsNode;
import net.linlan.dsh.cases.runtime.node.ModelSettingSaveNode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 模型设置 Pipeline 工厂。
 * <p>流程：按写/读目标组装四个独立 Pipeline，分别路由到保存、删除、配置列表和可用模型节点。</p>
 * <p>数据示例：保存传入 {@code channelCode=default} 返回配置 DTO；
 * 删除传入 {@code channelCode=default} 无返回；另两个查询无入参并返回列表。</p>
 */
@Service
public class RuntimePipelineFactory
        implements CasePipelineFactory<SaveModelSettingRequestDTO, NoContext, ModelSettingResponseDTO> {

    private final ModelSettingSaveNode modelSettingSaveNode;
    private final ModelSettingDeleteNode modelSettingDeleteNode;
    private final ModelSettingActivateNode modelSettingActivateNode;
    private final ModelSettingListSettingsNode modelSettingListSettingsNode;
    private final ModelSettingListAvailableModelsNode modelSettingListAvailableModelsNode;

    public RuntimePipelineFactory(
            ModelSettingSaveNode modelSettingSaveNode,
            ModelSettingDeleteNode modelSettingDeleteNode,
            ModelSettingActivateNode modelSettingActivateNode,
            ModelSettingListSettingsNode modelSettingListSettingsNode,
            ModelSettingListAvailableModelsNode modelSettingListAvailableModelsNode
    ) {
        this.modelSettingSaveNode = modelSettingSaveNode;
        this.modelSettingDeleteNode = modelSettingDeleteNode;
        this.modelSettingActivateNode = modelSettingActivateNode;
        this.modelSettingListSettingsNode = modelSettingListSettingsNode;
        this.modelSettingListAvailableModelsNode = modelSettingListAvailableModelsNode;
    }

    /**
     * 构建保存模型设置的 Pipeline。
     */
    public CasePipeline<SaveModelSettingRequestDTO, NoContext, ModelSettingResponseDTO> savePipeline() {
        return CasePipeline.of("Save model setting", modelSettingSaveNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建删除模型设置的 Pipeline。
     */
    public CasePipeline<DeleteModelSettingRequestDTO, NoContext, Void> deletePipeline() {
        return CasePipeline.of("Delete model setting", modelSettingDeleteNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建激活模型设置的 Pipeline。
     */
    public CasePipeline<ActivateModelSettingRequestDTO, NoContext, ModelSettingResponseDTO> activatePipeline() {
        return CasePipeline.of("Activate model setting", modelSettingActivateNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建查询模型设置列表的 Pipeline。
     */
    public CasePipeline<Void, NoContext, List<ModelSettingResponseDTO>> listSettingsPipeline() {
        return CasePipeline.of("List model settings", modelSettingListSettingsNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建查询可用模型列表的 Pipeline。
     */
    public CasePipeline<Void, NoContext, List<AvailableModelResponseDTO>> listAvailableModelsPipeline() {
        return CasePipeline.of("List available models", modelSettingListAvailableModelsNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建默认执行 Pipeline，绑定首节点和上下文。
     */
    @Override
    public CasePipeline<SaveModelSettingRequestDTO, NoContext, ModelSettingResponseDTO> pipeline() {
        return savePipeline();
    }
}
