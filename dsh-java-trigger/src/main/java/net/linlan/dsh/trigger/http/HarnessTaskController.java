package net.linlan.dsh.trigger.http;

import net.linlan.dsh.api.dto.SubmitTaskRequestDTO;
import net.linlan.dsh.api.IHarnessTaskApi;
import net.linlan.dsh.api.response.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务提交控制器。
 * <p>
 * 执行流程：
 * 1. 接收前端提交任务请求；
 * 2. 交给任务用例完成校验、排队和状态落库；
 * 3. 返回任务提交结果。
 *
 * <p>调用流程：HTTP/Web 层 → API 适配器 → 应用门面。</p>
 * <p>示例：控制器只负责协议转换，不写业务规则。</p>
 *
 */
@RestController
@RequestMapping("/api/harness/tasks")
public class HarnessTaskController {

    private final IHarnessTaskApi harnessTaskApi;

    /**
     * 处理Harness 任务 Controller。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param harnessTaskApi harness 任务 API参数。
     * @return 方法执行结果。
     */
    public HarnessTaskController(IHarnessTaskApi harnessTaskApi) {
        this.harnessTaskApi = harnessTaskApi;
    }

    /**
     * 提交一个任务。
     * <p>
     * 执行流程：
     * 1. 读取请求体；
     * 2. 调用任务 API 执行提交；
     * 3. 返回提交结果给前端。
     */
    @PostMapping("/submit")
    public Response<?> submitTask(@RequestBody SubmitTaskRequestDTO request) {
        return harnessTaskApi.submitTask(request);
    }
}
