package net.linlan.dsh.domain.channel;

import net.linlan.dsh.domain.channel.model.valobj.ChannelPresetVO;
import java.util.List;

/**
 * 渠道模板查询领域服务接口。
 * <p>
 * 执行流程：
 * 1. 读取渠道模板注册表；
 * 2. 映射为视图对象；
 * 3. 供用例层编排调用。
 *
 *
 */
public interface IChannelPresetQueryService {

    /**
     * 查询全部渠道模板。
     *
     * @return 模板视图对象列表
     */
    List<ChannelPresetVO> listPresets();
}
