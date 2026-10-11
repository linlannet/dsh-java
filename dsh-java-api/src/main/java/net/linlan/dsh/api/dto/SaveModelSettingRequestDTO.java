package net.linlan.dsh.api.dto;

/**
 * 承载用户编辑后的一条模型设置记录。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 * @param channelCode 模型渠道内部标识；新增时为空，由服务端生成，编辑时回传原值
 * @param displayName 用户可见的渠道名称
 * @param protocol 渠道协议编码（openai / anthropic / ollama），空值按 openai 兼容处理
 *
 */
public record SaveModelSettingRequestDTO(
        String channelCode,
        String displayName,
        String providerCode,
        String modelCode,
        String baseUrl,
        String apiKeyRef,
        String protocol,
        boolean enabled
) {
}
