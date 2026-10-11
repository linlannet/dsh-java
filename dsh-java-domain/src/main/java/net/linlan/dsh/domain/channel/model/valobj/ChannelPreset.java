package net.linlan.dsh.domain.channel.model.valobj;

import java.util.List;

/**
 * 渠道模板（厂商预设）。
 * <p>
 * 描述一种常见大模型渠道的默认接入参数：协议、默认地址、鉴权方式、推荐模型。
 * 模板只是「快捷填充」的数据源，用户保存配置时以数据库记录为准，
 * 模板本身不落库、不限制用户自定义（参考 WaLiAPI 的 ChannelPreset registry）。
 *
 * @param id              模板标识，同时作为默认 providerCode（如 deepseek、qwen、custom）
 * @param displayName     展示名称
 * @param protocol        通信协议
 * @param baseUrl         默认基础地址
 * @param authScheme      鉴权方式
 * @param modelSuggestions 推荐模型列表（供前端下拉预填，未同步上游前可用）
 * @param regionGroup     区域分组：international / domestic / local / custom
 *
 *
 */
public record ChannelPreset(
        String id,
        String displayName,
        ChannelProtocol protocol,
        String baseUrl,
        AuthScheme authScheme,
        List<String> modelSuggestions,
        String regionGroup
) {
    public ChannelPreset {
        modelSuggestions = modelSuggestions == null ? List.of() : List.copyOf(modelSuggestions);
    }
}
