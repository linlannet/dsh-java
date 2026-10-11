package net.linlan.dsh.api.dto;

import java.util.List;

/**
 * 渠道模板响应对象。
 * <p>
 * 供前端渲染「渠道模板」下拉：选择模板即可自动填充协议、默认地址与推荐模型。
 * 模板为只读数据，不落库；运行时的权威配置仍是模型设置记录。
 *
 * @param id               模板标识，同时作为默认 providerCode
 * @param displayName      展示名称
 * @param protocol         协议编码（openai / anthropic / ollama）
 * @param baseUrl          默认基础地址
 * @param authScheme       鉴权方式（BEARER / X_API_KEY / NONE）
 * @param modelSuggestions 推荐模型列表
 * @param regionGroup      区域分组（international / domestic / local / custom）
 *
 *
 */
public record ChannelPresetResponseDTO(
        String id,
        String displayName,
        String protocol,
        String baseUrl,
        String authScheme,
        List<String> modelSuggestions,
        String regionGroup
) {
}
