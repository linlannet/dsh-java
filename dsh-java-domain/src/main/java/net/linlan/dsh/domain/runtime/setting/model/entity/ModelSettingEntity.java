package net.linlan.dsh.domain.runtime.setting.model.entity;

import java.time.Instant;
import java.util.Objects;

/**
 * 模型渠道实体。
 */
public class ModelSettingEntity {

    private final String channelCode;
    private final String displayName;
    private final String providerCode;
    private final String modelCode;
    private final String baseUrl;
    private final String apiKeyRef;
    private final String protocol;
    private final boolean enabled;
    private final boolean active;
    private final Instant updatedAt;

    public ModelSettingEntity(
            String channelCode,
            String displayName,
            String providerCode,
            String modelCode,
            String baseUrl,
            String apiKeyRef,
            String protocol,
            boolean enabled,
            boolean active,
            Instant updatedAt
        ) {
        this.channelCode = requireText(channelCode, "channelCode");
        this.displayName = requireText(displayName, "displayName");
        this.providerCode = requireText(providerCode, "providerCode");
        this.modelCode = requireText(modelCode, "modelCode");
        this.baseUrl = blankToNull(baseUrl);
        this.apiKeyRef = blankToNull(apiKeyRef);
        this.protocol = blankToNull(protocol) == null ? "openai" : blankToNull(protocol);
        this.enabled = enabled;
        this.active = active;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static ModelSettingEntity create(
            String channelCode,
            String displayName,
            String providerCode,
            String modelCode,
            String baseUrl,
            String apiKeyRef,
            String protocol,
            boolean enabled,
            boolean active
        ) {
        return new ModelSettingEntity(
                channelCode, displayName, providerCode, modelCode, baseUrl, apiKeyRef,
                protocol, enabled, active, Instant.now()
        );
    }

    public String getChannelCode() { return channelCode; }
    public String getDisplayName() { return displayName; }
    public String getProviderCode() { return providerCode; }
    public String getModelCode() { return modelCode; }
    public String getBaseUrl() { return baseUrl; }
    public String getApiKeyRef() { return apiKeyRef; }
    public String getProtocol() { return protocol; }
    public boolean isEnabled() { return enabled; }
    public boolean isActive() { return active; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
