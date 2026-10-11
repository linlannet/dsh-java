package net.linlan.dsh.api.dto;

/**
 * 终端打开请求。
 * <p>
 * 执行流程：
 * 1. 前端提交名称与工作目录；
 * 2. API 层转交领域服务；
 * 3. 基础设施层启动 shell 进程。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
public record TerminalOpenRequestDTO(String name, String cwd) {
}
