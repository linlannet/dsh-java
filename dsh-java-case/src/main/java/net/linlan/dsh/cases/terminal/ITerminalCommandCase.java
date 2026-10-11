package net.linlan.dsh.cases.terminal;

import net.linlan.dsh.api.dto.TerminalOpenRequestDTO;
import net.linlan.dsh.api.dto.TerminalSendRequestDTO;
import net.linlan.dsh.api.dto.TerminalSendResponseDTO;
import net.linlan.dsh.api.dto.TerminalSessionResponseDTO;

/**
 * 终端打开、执行命令和关闭用例契约。
 * <p>流程：Trigger/前端 → 本接口 → TerminalPipelineFactory → 终端领域命令服务。</p>
 * <p>数据示例：打开传入 {@code name=build, cwd=/tmp/demo}；
 * 执行传入 {@code command=ls, timeoutMs=3000}；关闭传入 {@code sessionId=term-001}。</p>
 */
public interface ITerminalCommandCase {
    TerminalSessionResponseDTO open(TerminalOpenRequestDTO command);
    TerminalSendResponseDTO send(String sessionId, TerminalSendRequestDTO command);
    TerminalSessionResponseDTO close(String sessionId);
}
