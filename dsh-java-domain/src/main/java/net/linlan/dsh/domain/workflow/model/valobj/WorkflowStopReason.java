package net.linlan.dsh.domain.workflow.model.valobj;

/**
 * WorkflowStopReason 定义当前模块中的enum契约与职责。
 * <p>调用流程：定义工作流 → 启动 → 节点推进 → 事件/状态回写。</p>
 * <p>示例：工作流按节点顺序执行，每个节点可写回下一步上下文。</p>
 */
public enum WorkflowStopReason { COMPLETED, CANCELLED, ERROR }
