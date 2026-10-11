package net.linlan.dsh.domain.task.submission.model.valobj;

import net.linlan.dsh.domain.runtime.tool.model.valobj.ToolSelectionVO;

/**
 * 任务提交输入对象。
 * <p>
 * 执行流程：
 * 1. 上层提交工作区、提示词和执行画像；
 * 2. 领域层在此对象上完成后续规划；
 * 3. 传递给权限、路由和排队流程。
 *
 * @param workspaceId 工作区编码
 * @param prompt 用户提示词
 * @param profileCode 执行画像编码
 * @param channelCode 模型渠道编码；为空时使用当前激活渠道
 * @param toolSelection 请求的工具集合
 *
 *
 */
public record TaskSubmissionVO(
        String workspaceId,
        String prompt,
        String profileCode,
        String channelCode,
        ToolSelectionVO toolSelection
) {
}
