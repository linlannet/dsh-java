package net.linlan.dsh.domain.channel.service;

import net.linlan.dsh.domain.channel.IChannelPresetQueryService;
import net.linlan.dsh.domain.channel.model.valobj.ChannelPresetVO;
import net.linlan.dsh.domain.channel.model.valobj.ChannelPresets;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 渠道模板查询领域服务。
 * <p>
 * 执行流程：
 * 1. 读取 ChannelPresets 注册表；
 * 2. 逐条映射为 ChannelPresetVO；
 * 3. 返回给用例层。
 *
 *
 */
@Service
public class ChannelPresetQueryService implements IChannelPresetQueryService {

    @Override
    public List<ChannelPresetVO> listPresets() {
        return ChannelPresets.all().stream()
                .map(p -> new ChannelPresetVO(
                        p.id(),
                        p.displayName(),
                        p.protocol().code(),
                        p.baseUrl(),
                        p.authScheme().name(),
                        p.modelSuggestions(),
                        p.regionGroup()
                ))
                .toList();
    }
}
