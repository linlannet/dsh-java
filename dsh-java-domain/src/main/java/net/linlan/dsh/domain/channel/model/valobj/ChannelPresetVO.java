package net.linlan.dsh.domain.channel.model.valobj;

import java.util.List;

/**
 * 渠道模板视图对象（纯字符串/列表形态，供跨层传输）。
 * <p>由领域层从 {@link ChannelPreset} 映射生成，用例层与接口层直接消费，
 * 无需感知枚举类型。</p>
 *
 * @param id               模板标识
 * @param displayName      展示名称
 * @param protocol         协议编码（openai / anthropic / ollama）
 * @param baseUrl          默认基础地址
 * @param authScheme       鉴权方式编码（BEARER / X_API_KEY / NONE）
 * @param modelSuggestions 推荐模型列表
 * @param regionGroup      区域分组
 *
 *
 */
public record ChannelPresetVO(
        String id,
        String displayName,
        String protocol,
        String baseUrl,
        String authScheme,
        List<String> modelSuggestions,
        String regionGroup
) {
}
