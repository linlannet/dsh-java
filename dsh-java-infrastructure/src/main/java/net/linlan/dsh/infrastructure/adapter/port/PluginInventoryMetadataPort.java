package net.linlan.dsh.infrastructure.adapter.port;

import net.linlan.dsh.domain.plugin.registry.adapter.port.IPluginInventoryMetadataPort;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInventoryMetadataVO;
import net.linlan.dsh.infrastructure.gateway.PluginManifestGatewayService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.springframework.stereotype.Component;

@Component
public class PluginInventoryMetadataPort implements IPluginInventoryMetadataPort {

    private final PluginManifestGatewayService manifestGatewayService;

    public PluginInventoryMetadataPort(PluginManifestGatewayService manifestGatewayService) {
        this.manifestGatewayService = manifestGatewayService;
    }

    @Override
    public Optional<PluginInventoryMetadataVO> read(HarnessPluginEntity plugin) {
        Path sourcePath = Path.of(plugin.getSourcePath()).toAbsolutePath().normalize();
        if (Files.isRegularFile(sourcePath)) {
            return readJarMetadata(sourcePath);
        }
        return readJson(sourcePath.resolve(".codex-plugin/plugin.json"))
                .or(() -> readJson(sourcePath.resolve("package.json")))
                .map(manifest -> new PluginInventoryMetadataVO(
                        text(manifest, "manifestPath"),
                        author(manifest),
                        text(manifest, "description"),
                        normalize(text(manifest, "scope"), "project")
                ));
    }

    private Optional<Map<String, Object>> readJson(Path path) {
        try {
            return manifestGatewayService.readJson(path).map(manifest -> {
                manifest.put("manifestPath", path.toString());
                return manifest;
            });
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Optional<PluginInventoryMetadataVO> readJarMetadata(Path sourcePath) {
        if (!sourcePath.getFileName().toString().toLowerCase().endsWith(".jar")) {
            return Optional.empty();
        }
        try (JarFile jarFile = new JarFile(sourcePath.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                return Optional.of(new PluginInventoryMetadataVO(
                        sourcePath.toString(), null, null, "artifact"
                ));
            }
            Attributes attributes = manifest.getMainAttributes();
            return Optional.of(new PluginInventoryMetadataVO(
                    sourcePath + "!META-INF/MANIFEST.MF",
                    firstNonBlank(
                            attributes.getValue("Plugin-Author"),
                            attributes.getValue("Implementation-Vendor")
                    ),
                    firstNonBlank(
                            attributes.getValue("Plugin-Description"),
                            attributes.getValue("Implementation-Title")
                    ),
                    normalize(attributes.getValue("Plugin-Scope"), "artifact")
            ));
        } catch (Exception ignored) {
            return Optional.of(new PluginInventoryMetadataVO(
                    sourcePath.toString(), null, null, "artifact"
            ));
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value, null);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String text(Map<String, Object> manifest, String field) {
        Object value = manifest.get(field);
        return value == null ? null : normalize(String.valueOf(value), null);
    }

    private String author(Map<String, Object> manifest) {
        Object author = manifest.get("author");
        if (author instanceof Map<?, ?> map && map.get("name") != null) {
            return String.valueOf(map.get("name"));
        }
        return author == null ? null : normalize(String.valueOf(author), null);
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
