package net.linlan.dsh.cases.task;

import net.linlan.dsh.api.dto.SubmitTaskRequestDTO;
import net.linlan.dsh.api.dto.SubmitTaskResponseDTO;

/**
 * 编排任务提交用例。
 *
 * <p>调用流程：应用门面 → 用例编排 → 领域服务。</p>
 * <p>示例：调用方只依赖用例接口，不直接访问领域内部类。</p>
 *
 */
public interface ISubmitHarnessTaskCase {

    /**
     * 执行任务提交流程。
     *
     * @param request 任务请求
     * @return 已接收的任务摘要
     */
    SubmitTaskResponseDTO execute(SubmitTaskRequestDTO request);
}
