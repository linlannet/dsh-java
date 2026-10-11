package net.linlan.dsh.domain.workflow.model.valobj;

import java.util.Optional;

/**
 * 工作流阶段定义。
 * <p>
 * 执行流程：
 * 1. 脚本声明阶段信息；
 * 2. 运行时根据阶段标题或说明进行编排；
 * 3. 作为工作流展示和调试信息使用。
 *
 * @param title 阶段标题
 * @param detail 阶段说明
 * @param provider 提供方覆盖信息
 * @param model 模型覆盖信息
 *
 * <p>调用流程：定义工作流 → 启动 → 节点推进 → 事件/状态回写。</p>
 * <p>示例：工作流按节点顺序执行，每个节点可写回下一步上下文。</p>
 *
 */
public record WorkflowPhase(String title, Optional<String> detail, Optional<String> provider, Optional<String> model) {}
