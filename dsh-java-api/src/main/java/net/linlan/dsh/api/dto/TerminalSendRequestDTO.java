package net.linlan.dsh.api.dto;

/**
 * 终端发送请求。
 * <p>
 * 执行流程：
 * 1. 前端传入命令和超时；
 * 2. API 层转成领域调用；
 * 3. 返回本次命令输出。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
public record TerminalSendRequestDTO(String command, Long timeoutMs) {
}
