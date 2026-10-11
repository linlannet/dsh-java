package net.linlan.dsh.cases.runtime;

import net.linlan.dsh.api.dto.AvailableModelResponseDTO;
import net.linlan.dsh.api.dto.ModelSettingResponseDTO;
import net.linlan.dsh.cases.runtime.factory.RuntimePipelineFactory;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 模型设置读操作编排实现。
 * <p>流程：将查询目标交给对应 Pipeline → 执行领域查询节点 → 返回 DTO 列表。</p>
 * <p>数据示例：配置列表可包含 {@code channelCode=default, providerCode=openai-compatible}；
 * 可用模型仅保留 {@code enabled=true} 且 {@code modelCode} 非空的记录。</p>
 */
@Service
public class ModelSettingQueryCaseImpl implements IModelSettingQueryCase {
    private final RuntimePipelineFactory runtimePipelineFactory;

    public ModelSettingQueryCaseImpl(RuntimePipelineFactory runtimePipelineFactory) {
        this.runtimePipelineFactory = runtimePipelineFactory;
    }

    /**
     * 编排配置列表查询，执行查询 Pipeline 并返回 DTO 列表。
     */
    @Override
    public List<ModelSettingResponseDTO> listSettings() {
        return runtimePipelineFactory.listSettingsPipeline().execute(null);
    }

    /**
     * 编排可用模型查询，执行去重过滤后返回结果。
     */
    @Override
    public List<AvailableModelResponseDTO> listAvailableModels() {
        return runtimePipelineFactory.listAvailableModelsPipeline().execute(null);
    }
}
