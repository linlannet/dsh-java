package net.linlan.dsh.domain.agent.model.valobj;

/**
 * ApprovalModeVO 表示一次智能体会话的运行审批模式。
 */
public enum ApprovalModeVO {
    REQUEST_APPROVAL,
    AUTO_APPROVE,
    FULL_OPEN;

    public static ApprovalModeVO from(String value) {
        if (value == null || value.isBlank()) {
            return REQUEST_APPROVAL;
        }
        return valueOf(value.trim().toUpperCase().replace('-', '_'));
    }
}
