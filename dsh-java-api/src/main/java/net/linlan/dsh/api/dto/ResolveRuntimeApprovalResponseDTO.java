package net.linlan.dsh.api.dto;

public record ResolveRuntimeApprovalResponseDTO(boolean resolved, String verdict, String error) {}
