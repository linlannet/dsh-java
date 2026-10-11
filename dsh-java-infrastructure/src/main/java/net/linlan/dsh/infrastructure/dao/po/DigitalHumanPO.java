package net.linlan.dsh.infrastructure.dao.po;

import java.time.Instant;

public record DigitalHumanPO(String id, String displayName, String avatarRef, String purpose, String roleTags, String themeColor, String approvalPolicy, Integer concurrencyLimit, String projectPath, Instant createdAt, Instant updatedAt) {
}

