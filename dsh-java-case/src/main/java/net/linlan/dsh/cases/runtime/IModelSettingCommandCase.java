package net.linlan.dsh.cases.runtime;

import net.linlan.dsh.api.dto.ActivateModelSettingRequestDTO;
import net.linlan.dsh.api.dto.DeleteModelSettingRequestDTO;
import net.linlan.dsh.api.dto.SaveModelSettingRequestDTO;
import net.linlan.dsh.api.dto.ModelSettingResponseDTO;

/**
 * 模型设置写操作用例契约。
 * <p>流程：Trigger/前端 → 本接口 → 写 Pipeline → 模型设置领域命令服务。</p>
 * <p>数据示例：保存传入 {@code channelCode=default, modelCode=Deepseek-V4.1-Flash}；
 * 删除传入 {@code channelCode=default} 删除该渠道配置；
 * 激活传入 {@code channelCode=default} 将其切换为当前生效模型渠道。</p>
 */
public interface IModelSettingCommandCase {
    ModelSettingResponseDTO save(SaveModelSettingRequestDTO command);
    void delete(DeleteModelSettingRequestDTO command);
    ModelSettingResponseDTO activate(ActivateModelSettingRequestDTO command);
}
