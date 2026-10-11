package net.linlan.dsh.api.dto;

/**
 * 返回当前 LLM 运行时可用的一个模型渠道。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
public record AvailableModelResponseDTO(
        String channelCode,
        String displayName,
        String providerCode,
        String modelCode
) {
}
