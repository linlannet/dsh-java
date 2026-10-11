package net.linlan.dsh.api.dto;

/**
 * 终端读取响应。
 * <p>
 * 执行流程：
 * 1. 读取当前输出片段；
 * 2. 映射为响应体；
 * 3. 返回给前端轮询。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
public record TerminalReadResponseDTO(
        String sessionId,
        String content,
        boolean truncated,
        String status,
        Integer exitCode
) {

}
