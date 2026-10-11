package net.linlan.dsh.api.dto;

public record PluginArtifactCandidateDTO(
        String pluginId,
        String displayName,
        String pluginVersion,
        String runtimeType,
        String entrypoint,
        String author,
        String description,
        String sourcePath,
        String groupId,
        String artifactId,
        String version,
        boolean valid,
        String message,
        String sourceType
) {
}
