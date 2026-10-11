package net.linlan.dsh.trigger.service;

import net.linlan.dsh.api.dto.SubmitTaskRequestDTO;
import net.linlan.dsh.api.dto.SubmitTaskResponseDTO;
import net.linlan.dsh.cases.task.ISubmitHarnessTaskCase;
import net.linlan.dsh.api.IHarnessTaskApi;
import net.linlan.dsh.api.response.Response;
import org.springframework.stereotype.Service;

/**
 * 任务提交适配器。
 * <p>
 * 执行流程：
 * 1. 接收 Trigger 层提交的任务请求 DTO（SubmitTaskRequestDTO）；
 * 2. 直接调用任务提交用例编排；
 * 3. 用例返回 API 响应 DTO（SubmitTaskResponseDTO）。
 * <p>
 * 这一层只负责外部协议适配，不承载流程编排。
 *
 *
 */
@Service
public class HarnessTaskApi implements IHarnessTaskApi {

    private final ISubmitHarnessTaskCase submitHarnessTaskCase;

    /**
     * 处理Harness 任务 API。
     * 流程：转换协议 → 调用应用门面 → 返回结果。
     * @param submitHarnessTaskCase submit Harness 任务 Case参数。
     * @return 方法执行结果。
     */
    public HarnessTaskApi(ISubmitHarnessTaskCase submitHarnessTaskCase) {
        this.submitHarnessTaskCase = submitHarnessTaskCase;
    }

    @Override
    public Response<SubmitTaskResponseDTO> submitTask(SubmitTaskRequestDTO request) {
        try {
            return Response.success(submitHarnessTaskCase.execute(request));
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }

    // ── DTO ↔ Command 映射 ───────────────────────────────────────

    /**
     * 转换为 命令。
     * 流程：转换协议 → 调用应用门面 → 返回结果。
     * @param request 请求对象。
     * @return 转换后的目标对象。
     */
}
