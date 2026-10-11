package net.linlan.dsh.domain.session.registry.model.aggregate;

import net.linlan.dsh.domain.shared.model.valobj.HarnessStatusEnumVO;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents the durable lifecycle state for one harness session.
 *
 * <p>调用流程：事件写入 SessionEventLog → 事件溯源重建会话 → 查询或恢复。</p>
 * <p>示例：追加用户消息、模型消息和工具结果事件后，可按序号回放完整对话。</p>
 *
 */
public class HarnessSessionAggregate {

    private final String sessionId;
    private final String workspaceId;
    private final String profileCode;
    private final Instant createdAt;
    private String executionMode;
    private String resolvedChannelCode;
    private String resolvedModel;
    private HarnessStatusEnumVO status;
    private Instant lastActivityAt;

    /**
     * 处理Harness 会话 Aggregate。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @param workspaceId 工作区 ID。
     * @param profileCode profile 编码参数。
     * @param createdAt created At参数。
     * @return 方法执行结果。
     */
    public HarnessSessionAggregate(String sessionId, String workspaceId, String profileCode, Instant createdAt) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.profileCode = Objects.requireNonNull(profileCode, "profileCode");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = HarnessStatusEnumVO.CREATED;
    }

    /**
     * 获取Session Id。
     */
    public String getSessionId() { return sessionId; }
    /**
     * 获取WorkspaceIdID。
     * @return 领域处理结果。
     */
    public String getWorkspaceId() { return workspaceId; }
    /**
     * 获取Profile Code。
     */
    public String getProfileCode() { return profileCode; }
    /**
     * 获取Created At。
     */
    public Instant getCreatedAt() { return createdAt; }
    /**
     * 获取Execution Mode。
     */
    public String getExecutionMode() { return executionMode; }
    /**
     * 获取Resolved Channel Code。
     */
    public String getResolvedChannelCode() { return resolvedChannelCode; }
    /**
     * 获取Resolved Model。
     */
    public String getResolvedModel() { return resolvedModel; }
    /**
     * 获取Model Code。
     */
    public String getModelCode() { return resolvedModel; }
    /**
     * 获取Status。
     */
    public HarnessStatusEnumVO getStatus() { return status; }
    /**
     * 获取Last Activity At。
     */
    public Instant getLastActivityAt() { return lastActivityAt != null ? lastActivityAt : createdAt; }

    /**
     * 处理prepare。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param executionMode execution 模式参数。
     * @param resolvedModel resolved 模型参数。
     */
    public void prepare(String executionMode, String resolvedChannelCode, String resolvedModel) {
        this.executionMode = Objects.requireNonNull(executionMode, "executionMode");
        this.resolvedChannelCode = resolvedChannelCode;
        this.resolvedModel = Objects.requireNonNull(resolvedModel, "resolvedModel");
    }

    /**
     * 转换为 uch Activity。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     */
    public void touchActivity() {
        this.lastActivityAt = Instant.now();
    }

    /**
     * 设置Last Activity At。
     */
    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    /**
     * 处理change Status。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param status 状态对象。
     */
    public void changeStatus(HarnessStatusEnumVO status) {
        this.status = Objects.requireNonNull(status, "status");
    }
}
