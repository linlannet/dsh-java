package net.linlan.dsh.domain.task.execution.adapter.port;

import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.task.execution.model.valobj.ModelExecutionResultVO;

/**
 * 模型执行端口。
 * <p>
 * 执行流程：
 * 1. 领域层提交执行画像和提示词；
 * 2. 基础设施层调用具体模型提供方；
 * 3. 返回模型回复及相关元数据。
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
public interface IModelExecutionPort {

    /**
     * 执行一次已经完成路由和消息装配的模型请求。
     */
    ModelExecutionResultVO execute(GenerateOptions options);
}
