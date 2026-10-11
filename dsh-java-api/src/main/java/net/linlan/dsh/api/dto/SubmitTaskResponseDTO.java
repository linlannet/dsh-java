package net.linlan.dsh.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * 任务提交响应。
 *
 * @param taskId 生成的任务 ID
 * @param sessionId 生成的会话 ID
 * @param status 当前生命周期状态
 * @param resolvedChannelCode 解析后的模型渠道编码
 * @param resolvedModel 解析后的模型 ID
 * @param enabledTools 启用的工具列表
 * @param approvalRequired 执行前是否需要人工审批
 * @param approvalReason 审批决策的简要说明
 * @param acceptedAt 接收时间
 *
 *
 */
public record SubmitTaskResponseDTO(
        String taskId,
        String sessionId,
        String status,
        String resolvedChannelCode,
        String resolvedModel,
        List<String> enabledTools,
        boolean approvalRequired,
        String approvalReason,
        Instant acceptedAt
) {
}
