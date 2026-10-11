package net.linlan.dsh.cases.runtime;

import net.linlan.dsh.api.dto.AvailableModelResponseDTO;
import net.linlan.dsh.api.dto.ModelSettingResponseDTO;
import java.util.List;

/**
 * 模型设置读操作用例契约。
 * <p>流程：Trigger/前端 → 本接口 → 查询 Pipeline → 模型设置领域查询服务 → DTO 列表。</p>
 * <p>数据示例：无入参；配置列表返回 {@code channelCode=default, baseUrl=http://127.0.0.1:28080/v1}；
 * 可用模型返回 {@code modelCode=Deepseek-V4.1-Flash}。</p>
 */
public interface IModelSettingQueryCase {
    List<ModelSettingResponseDTO> listSettings();
    List<AvailableModelResponseDTO> listAvailableModels();
}
