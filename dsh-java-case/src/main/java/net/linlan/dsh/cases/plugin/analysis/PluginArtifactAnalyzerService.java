package net.linlan.dsh.cases.plugin.analysis;

import net.linlan.dsh.api.dto.PluginArtifactCandidateDTO;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.yaml.snakeyaml.Yaml;

@Service
public class PluginArtifactAnalyzerService implements IPluginArtifactAnalyzer {

    private static final String PLUGIN_MANIFEST = "META-INF/plugin.yaml";

    @Override
    public PluginArtifactCandidateDTO analyzeJar(Path jarPath) {
        Path normalized = jarPath == null ? null : jarPath.toAbsolutePath().normalize();
        if (normalized == null || !Files.exists(normalized) || !Files.isRegularFile(normalized)) {
            return invalid(normalized == null ? "" : normalized.toString(), "JAR 文件不存在");
        }
        if (!normalized.toString().toLowerCase().endsWith(".jar")) {
            return invalid(normalized.toString(), "请选择 .jar 文件");
        }
        try (JarFile jar = new JarFile(normalized.toFile())) {
            JarEntry entry = jar.getJarEntry(PLUGIN_MANIFEST);
            if (entry == null) {
                return invalid(normalized.toString(), "不是 DeepSeek Harness Java 插件：缺少 META-INF/plugin.yaml");
            }
            try (InputStream in = jar.getInputStream(entry)) {
                Map<String, Object> manifest = new Yaml().load(in);
                if (manifest == null) {
                    return invalid(normalized.toString(), "plugin.yaml 为空");
                }
                String id = text(manifest.get("id"));
                String name = text(manifest.get("name"));
                String version = text(manifest.get("version"));
                String entrypoint = text(manifest.get("entrypoint"));
                boolean hasSpi = jar.getJarEntry("META-INF/services/net.linlan.dsh.domain.spi.JavaHarnessPlugin") != null;
                if (id == null || name == null || version == null) {
                    return invalid(normalized.toString(), "plugin.yaml 缺少 id/name/version 必填字段");
                }
                if (entrypoint == null && !hasSpi) {
                    return invalid(normalized.toString(), "plugin.yaml 缺少 entrypoint，且未发现 Java SPI 声明");
                }
                return new PluginArtifactCandidateDTO(
                        id, name, version, "JAVA_NATIVE", entrypoint,
                        text(manifest.get("author")), text(manifest.get("description")),
                        normalized.toString(), null, null, null,
                        true, hasSpi ? "插件清单与 SPI 均有效" : "插件清单有效", "JAR"
                );
            }
        } catch (Exception exception) {
            return invalid(normalized.toString(), "JAR 解析失败：" + exception.getMessage());
        }
    }

    @Override
    public List<PluginArtifactCandidateDTO> analyzeMavenDependency(String pomXml) {
        List<PluginArtifactCandidateDTO> candidates = new ArrayList<>();
        if (pomXml == null || pomXml.isBlank()) {
            candidates.add(invalid(null, "请粘贴 POM 依赖片段"));
            return candidates;
        }
        try {
            String source = pomXml.trim();
            if (!source.startsWith("<project")) {
                source = "<root>" + source + "</root>";
            }
            Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(source)));
            Element root = document.getDocumentElement();
            Map<String, String> properties = readProperties(root);
            List<Element> containers = childElements(root, "dependencies");
            List<Element> dependencies;
            if (!containers.isEmpty()) {
                dependencies = containers.stream()
                        .flatMap(node -> childElements(node, "dependency").stream())
                        .toList();
            } else if ("dependencies".equals(root.getTagName())) {
                dependencies = childElements(root, "dependency");
            } else if ("dependency".equals(root.getTagName())) {
                dependencies = List.of(root);
            } else {
                dependencies = childElements(root, "dependency");
            }
            if (dependencies.isEmpty()) {
                dependencies = List.of(root);
            }
            for (Element dependency : dependencies) {
                String groupId = property(childText(dependency, "groupId"), properties);
                String artifactId = property(childText(dependency, "artifactId"), properties);
                String version = property(childText(dependency, "version"), properties);
                if (groupId == null || artifactId == null || version == null) {
                    candidates.add(mavenInvalid(groupId, artifactId, version,
                            "依赖缺少 groupId/artifactId/version"));
                    continue;
                }
                Path jar = localMavenJar(groupId, artifactId, version);
                if (!Files.exists(jar)) {
                    candidates.add(mavenInvalid(groupId, artifactId, version,
                            "本地 Maven 仓库未找到：" + jar + "；请先执行 mvn install 或 mvn dependency:copy-dependencies"));
                    continue;
                }
                PluginArtifactCandidateDTO result = analyzeJar(jar);
                candidates.add(new PluginArtifactCandidateDTO(
                        result.pluginId(), result.displayName(), result.pluginVersion(), result.runtimeType(),
                        result.entrypoint(), result.author(), result.description(), result.sourcePath(),
                        groupId, artifactId, version, result.valid(), result.message(), "POM"
                ));
            }
            return candidates;
        } catch (Exception exception) {
            candidates.add(invalid(null, "POM 解析失败：" + exception.getMessage()));
            return candidates;
        }
    }

    private Map<String, String> readProperties(Element root) {
        Map<String, String> properties = new HashMap<>();
        for (Element dependencies : childElements(root, "properties")) {
            NodeList children = dependencies.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node instanceof Element element) {
                    properties.put(element.getTagName(), element.getTextContent().trim());
                }
            }
        }
        return properties;
    }

    private List<Element> childElements(Element parent, String tagName) {
        List<Element> elements = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && tagName.equals(element.getTagName())) {
                elements.add(element);
            }
        }
        return elements;
    }

    private String childText(Element parent, String tagName) {
        List<Element> elements = childElements(parent, tagName);
        return elements.isEmpty() ? null : elements.get(0).getTextContent().trim();
    }

    private String property(String value, Map<String, String> properties) {
        if (value == null) return null;
        if (value.startsWith("${") && value.endsWith("}")) {
            String key = value.substring(2, value.length() - 1);
            return properties.getOrDefault(key, value);
        }
        return value;
    }

    private Path localMavenJar(String groupId, String artifactId, String version) {
        String groupPath = groupId.replace('.', '/');
        return Path.of(System.getProperty("user.home"), ".m2", "repository", groupPath, artifactId, version,
                artifactId + "-" + version + ".jar").toAbsolutePath().normalize();
    }

    private PluginArtifactCandidateDTO invalid(String sourcePath, String message) {
        return new PluginArtifactCandidateDTO(null, null, null, null, null, null, null,
                sourcePath, null, null, null, false, message, "JAR");
    }

    private PluginArtifactCandidateDTO mavenInvalid(String groupId, String artifactId, String version, String message) {
        return new PluginArtifactCandidateDTO(null, null, null, null, null, null, null,
                null, groupId, artifactId, version, false, message, "POM");
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }
}
