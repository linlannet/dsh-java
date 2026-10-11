package net.linlan.dsh.infrastructure.adapter.port;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallModeEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallStatusEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginRuntimeTypeEnumVO;
import net.linlan.dsh.infrastructure.gateway.PluginManifestGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginInventoryMetadataPortTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadManifestMetadata() throws Exception {
        Path sourcePath = Files.createDirectories(tempDir.resolve("weather"));
        Path manifestPath = Files.createDirectories(sourcePath.resolve(".codex-plugin")).resolve("plugin.json");
        Files.writeString(manifestPath, """
                {"name":"weather","author":{"name":"Alice"},"description":"Weather tools","scope":"project"}
                """);
        var port = new PluginInventoryMetadataPort(new PluginManifestGatewayService(new ObjectMapper()));

        var metadata = port.read(plugin(sourcePath)).orElseThrow();

        assertEquals(manifestPath.toString(), metadata.manifestPath());
        assertEquals("Alice", metadata.author());
        assertEquals("Weather tools", metadata.description());
        assertEquals("project", metadata.scope());
    }

    @Test
    void shouldReadArtifactFileMetadata() throws Exception {
        Path sourcePath = Files.createFile(tempDir.resolve("weather.jar"));
        var port = new PluginInventoryMetadataPort(new PluginManifestGatewayService(new ObjectMapper()));

        var metadata = port.read(plugin(sourcePath)).orElseThrow();

        assertEquals("artifact", metadata.scope());
    }

    @Test
    void shouldReadJarManifestMetadata() throws Exception {
        Path sourcePath = tempDir.resolve("weather.jar");
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Plugin-Author", "Bob");
        attributes.putValue("Plugin-Description", "Weather tools");
        attributes.putValue("Plugin-Scope", " project ");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(sourcePath), manifest)) {
        }
        var port = new PluginInventoryMetadataPort(new PluginManifestGatewayService(new ObjectMapper()));

        var metadata = port.read(plugin(sourcePath)).orElseThrow();

        assertEquals(sourcePath + "!META-INF/MANIFEST.MF", metadata.manifestPath());
        assertEquals("Bob", metadata.author());
        assertEquals("Weather tools", metadata.description());
        assertEquals("project", metadata.scope());
    }

    @Test
    void shouldFallbackWhenJarManifestIsMissing() throws Exception {
        Path sourcePath = tempDir.resolve("plain.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(sourcePath))) {
        }
        var port = new PluginInventoryMetadataPort(new PluginManifestGatewayService(new ObjectMapper()));

        var metadata = port.read(plugin(sourcePath)).orElseThrow();

        assertEquals(sourcePath.toString(), metadata.manifestPath());
        assertEquals("artifact", metadata.scope());
    }

    @Test
    void shouldNotFailWhenManifestIsCorrupt() throws Exception {
        Path sourcePath = Files.createDirectories(tempDir.resolve("corrupt"));
        Files.createDirectories(sourcePath.resolve(".codex-plugin"));
        Files.writeString(sourcePath.resolve(".codex-plugin/plugin.json"), "{broken");
        var port = new PluginInventoryMetadataPort(new PluginManifestGatewayService(new ObjectMapper()));

        var metadata = port.read(plugin(sourcePath));

        assertEquals(java.util.Optional.empty(), metadata);
    }

    @Test
    void shouldNormalizeBlankScopeToProject() throws Exception {
        Path sourcePath = Files.createDirectories(tempDir.resolve("blank-scope"));
        Files.createDirectories(sourcePath.resolve(".codex-plugin"));
        Files.writeString(sourcePath.resolve(".codex-plugin/plugin.json"), """
                {"scope":"  "}
                """);
        var port = new PluginInventoryMetadataPort(new PluginManifestGatewayService(new ObjectMapper()));

        var metadata = port.read(plugin(sourcePath)).orElseThrow();

        assertEquals("project", metadata.scope());
    }

    private HarnessPluginEntity plugin(Path sourcePath) {
        return new HarnessPluginEntity(
                "weather",
                "Weather",
                "1.0.0",
                PluginRuntimeTypeEnumVO.DSH_NODE_BRIDGE,
                PluginInstallModeEnumVO.NODE_BRIDGE,
                sourcePath.toString(),
                "index.js",
                PluginInstallStatusEnumVO.ACTIVE,
                Instant.EPOCH
        );
    }
}
