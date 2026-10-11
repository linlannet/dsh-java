package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.ActivateModelSettingRequestDTO;
import net.linlan.dsh.api.dto.ModelSettingResponseDTO;
import net.linlan.dsh.api.dto.SaveModelSettingRequestDTO;
import net.linlan.dsh.api.response.Response;

public interface IHarnessModelSettingCommandApi {

    Response<ModelSettingResponseDTO> save(SaveModelSettingRequestDTO request);

    /**
     * 删除模型设置。
     *
     */
    Response<Void> delete(String channelCode);

    /**
     * 将指定模型渠道切换为当前生效渠道。
     */
    Response<ModelSettingResponseDTO> activate(ActivateModelSettingRequestDTO request);
}
