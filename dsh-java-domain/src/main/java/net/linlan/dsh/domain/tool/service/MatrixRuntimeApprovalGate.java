package net.linlan.dsh.domain.tool.service;

import net.linlan.dsh.domain.task.permission.adapter.port.IPermissionMatrixPort;
import net.linlan.dsh.domain.agent.model.valobj.ApprovalModeVO;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * 基于权限矩阵的运行期审批门禁。
 * <p>
 * 语义：
 * <ul>
 *   <li>不在「需审批工具」集合内的调用直接放行；</li>
 *   <li>在集合内且已被 {@link #allowForSession(String)} 标记的调用直接放行；</li>
 *   <li>否则进入「询问」流程：调用 {@code askHandler} 阻塞等待人工裁决
     *   （allow-once / allow-session / deny / cancel），对应 TS 的
     *   gate.kind === 'ask' 实时挂起语义；</li>
 *   <li>未配置 askHandler 时退化为防御性 DENY（保持旧行为）。</li>
 * </ul>
 *
 *
 */
public final class MatrixRuntimeApprovalGate implements RuntimeApprovalGate {

    private final IPermissionMatrixPort permissionMatrixPort;
    private final Set<String> sessionAllowed = ConcurrentHashMap.newKeySet();
    private final String sessionId;
    private final BiFunction<ApprovalRequest, MatrixRuntimeApprovalGate, ApprovalVerdict> askHandler;
    private volatile ApprovalModeVO approvalMode = ApprovalModeVO.REQUEST_APPROVAL;

    /**
     * 处理Matrix 运行时 审批 Gate。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param permissionMatrixPort permission Matrix Port参数。
     * @return 方法执行结果。
     */
    public MatrixRuntimeApprovalGate(IPermissionMatrixPort permissionMatrixPort) {
        this(permissionMatrixPort, null, null);
    }

    /**
     * @param permissionMatrixPort 权限矩阵
     * @param sessionId            所属会话（用于审批请求归属）
     * @param askHandler           「询问」处理器：接收审批请求，阻塞等待并返回裁决；
     *                             为 null 时需审批工具一律 DENY
     */
    public MatrixRuntimeApprovalGate(
            IPermissionMatrixPort permissionMatrixPort,
            String sessionId,
            BiFunction<ApprovalRequest, MatrixRuntimeApprovalGate, ApprovalVerdict> askHandler
    ) {
        this.permissionMatrixPort = permissionMatrixPort;
        this.sessionId = sessionId;
        this.askHandler = askHandler;
    }

    /** 标记某工具在本会话内放行（对应 TS 的 allowed-session）。 */
    public void allowForSession(String toolName) {
        if (toolName != null) sessionAllowed.add(toolName);
    }

    public void setApprovalMode(ApprovalModeVO mode) {
        this.approvalMode = mode == null ? ApprovalModeVO.REQUEST_APPROVAL : mode;
    }

    @Override
    public boolean requiresApproval(String toolName) {
        if (toolName == null || sessionAllowed.contains(toolName)) {
            return false;
        }
        if (approvalMode == ApprovalModeVO.FULL_OPEN || approvalMode == ApprovalModeVO.AUTO_APPROVE) {
            return false;
        }
        var matrix = permissionMatrixPort.load();
        return matrix.approvalRequiredTools().contains(toolName);
    }

    @Override
    public Decision check(String toolName, Object arguments) {
        if (sessionAllowed.contains(toolName)) return Decision.ALLOW;
        if (approvalMode == ApprovalModeVO.FULL_OPEN) return Decision.ALLOW;
        var matrix = permissionMatrixPort.load();
        if (!matrix.approvalRequiredTools().contains(toolName)) {
            return Decision.ALLOW;
        }
        if (approvalMode == ApprovalModeVO.AUTO_APPROVE) return Decision.ALLOW;
        if (askHandler == null) {
            return Decision.DENY;
        }
        ApprovalVerdict verdict = ask(toolName, arguments);
        return switch (verdict) {
            case ALLOW_ONCE -> Decision.ALLOW;
            case ALLOW_SESSION -> {
                allowForSession(toolName);
                yield Decision.ALLOW;
            }
            case DENY, CANCEL -> Decision.DENY;
        };
    }

    /** 工具调用的可展示摘要（命令/路径等关键参数）。 */
    private String summarize(String toolName, Object arguments) {
        if (arguments instanceof Map<?, ?> map) {
            Object command = map.get("command");
            if (command != null) return String.valueOf(command);
            Object path = map.get("path");
            if (path != null) return String.valueOf(path);
            Object directory = map.get("directory");
            if (directory != null) return String.valueOf(directory);
        }
        return toolName;
    }

    /**
     * 询问目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param toolName 工具 名称参数。
     * @param arguments arguments参数。
     * @return 方法执行结果。
     */
    private ApprovalVerdict ask(String toolName, Object arguments) {
        @SuppressWarnings("unchecked")
        Map<String, Object> argMap = arguments instanceof Map ? (Map<String, Object>) arguments : Map.of();
        return askHandler.apply(
                new ApprovalRequest(sessionId, toolName, summarize(toolName, arguments), argMap),
                this
        );
    }
}
