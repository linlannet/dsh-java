package net.linlan.dsh.trigger.service.query;

import net.linlan.dsh.api.IHarnessChannelPresetApi;
import net.linlan.dsh.api.dto.ChannelPresetResponseDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.runtime.IChannelPresetQueryCase;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 渠道模板查询适配器。
 * <p>流程：委托渠道模板用例读取注册表 → 包装为统一响应；模板只读不落库。</p>
 *
 *
 */
@Service
public class HarnessChannelPresetQueryApi implements IHarnessChannelPresetApi {

    private final IChannelPresetQueryCase channelPresetQueryCase;

    public HarnessChannelPresetQueryApi(IChannelPresetQueryCase channelPresetQueryCase) {
        this.channelPresetQueryCase = channelPresetQueryCase;
    }

    @Override
    public Response<List<ChannelPresetResponseDTO>> listPresets() {
        try {
            return Response.success(channelPresetQueryCase.listPresets());
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }
}
