package net.linlan.dsh.domain.sandbox.service;

import net.linlan.dsh.domain.sandbox.ISandboxPolicyService;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxExecutionPolicy;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxPolicyRequest;

import java.nio.file.Path;
import java.util.Optional;

/**
 * SandboxPolicyInjector 每次工具调用时解析沙箱策略，序列化策略摘要，注入 AssembleContext。
 */
public final class SandboxPolicyInjector {

    private final ISandboxPolicyService policyService;

    public SandboxPolicyInjector(ISandboxPolicyService policyService) {
        if (policyService == null) {
            throw new IllegalArgumentException("policyService must not be null");
        }
        this.policyService = policyService;
    }

    public String injectPolicy(String toolName, String arguments) {
        SandboxPolicyRequest request = new SandboxPolicyRequest(Optional.empty(), Optional.empty());
        SandboxExecutionPolicy policy = policyService.resolve(request);
        return serializeSummary(toolName, arguments, policy);
    }

    public String injectPolicy(String toolName, String arguments, SandboxPolicyRequest request) {
        SandboxPolicyRequest req = request != null ? request : new SandboxPolicyRequest(Optional.empty(), Optional.empty());
        SandboxExecutionPolicy policy = policyService.resolve(req);
        return serializeSummary(toolName, arguments, policy);
    }

    private String serializeSummary(String toolName, String arguments, SandboxExecutionPolicy policy) {
        String safeTool = toolName == null ? "" : toolName;
        String safeArgs = arguments == null ? "" : arguments;
        String mode = policy.mode().name();
        String root = policy.workspaceRoot().map(Path::toString).orElse("");
        return "sandbox-policy{tool=" + safeTool
                + ", mode=" + mode
                + ", workspaceRoot=" + root
                + ", arguments=" + safeArgs + "}";
    }
}
