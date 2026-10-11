package net.linlan.dsh.domain.sdk.model.valobj;

/**
 * ResourceInfo 是资源列表项的信息。
 */
public record ResourceInfo(
        String uri,
        String name,
        String description
) {}
