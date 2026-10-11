package net.linlan.dsh.api.dto;

/**
 * 激活模型设置请求：将指定模型渠道切换为当前生效渠道。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 * @param channelCode 模型渠道编码
 *
 */
public record ActivateModelSettingRequestDTO(
        String channelCode
) {
}
