package net.linlan.dsh.cases.runtime;

import net.linlan.dsh.api.dto.ChannelPresetResponseDTO;
import java.util.List;

/**
 * 渠道模板查询用例。
 * <p>读取领域层渠道模板注册表并映射为 DTO；模板只读不落库。</p>
 *
 *
 */
public interface IChannelPresetQueryCase {

    /**
     * 查询全部渠道模板。
     *
     * @return 模板列表
     */
    List<ChannelPresetResponseDTO> listPresets();
}
