package net.linlan.dsh.domain.runtime.setting.model.valobj;

import java.time.Instant;

/**
 * 模型设置视图对象。
 * <p>
 * 执行流程：
 * 1. 仓储读出模型设置实体；
 * 2. 领域服务转换成视图对象；
 * 3. API 层直接映射给前端响应。
 *
 * @param channelCode 模型渠道编码
 * @param providerCode 提供方编码
 * @param modelCode 模型编码
 * @param baseUrl 提供方基础地址
 * @param apiKeyRef API Key 引用
 * @param protocol 渠道协议编码（openai / anthropic / ollama），空值按 openai 处理
 * @param enabled 是否启用
 * @param active 是否为当前生效的模型渠道
 * @param updatedAt 更新时间
 *
 *
 */
public record ModelSettingVO(
        String channelCode,
        String displayName,
        String providerCode,
        String modelCode,
        String baseUrl,
        String apiKeyRef,
        String protocol,
        boolean enabled,
        boolean active,
        Instant updatedAt
) {
    /**
     * 兼容旧调用方的紧凑构造（active 默认 false）。
     */
    public ModelSettingVO(String channelCode, String displayName, String providerCode, String modelCode,
                          String baseUrl, String apiKeyRef, String protocol,
                          boolean enabled, Instant updatedAt) {
        this(channelCode, displayName, providerCode, modelCode, baseUrl, apiKeyRef, protocol, enabled, false, updatedAt);
    }
}
