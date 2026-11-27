package net.linlan.dsh.domain.runtime.profile.bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** BundleAssembler 按 BundleDefinition 装配插件行、状态与配置。 */
public class BundleAssembler {

    private static final Logger log = LoggerFactory.getLogger(BundleAssembler.class);
    private static final String UPSTREAM_SKILLS = "upstream-skills";

    private final Map<String, BundleDefinition> knownBundles;
    private final boolean skillEnabled;

    public BundleAssembler() {
        this(defaultBundles(), true, log::warn);
    }

    public BundleAssembler(boolean skillEnabled) {
        this(defaultBundles(), skillEnabled, log::warn);
    }

    public BundleAssembler(List<BundleDefinition> bundles) {
        this(bundles, true, log::warn);
    }

    public BundleAssembler(List<BundleDefinition> bundles, boolean skillEnabled,
                           Consumer<String> startupWarning) {
        Map<String, BundleDefinition> indexed = new LinkedHashMap<>();
        for (BundleDefinition bundle : bundles) {
            indexed.put(bundle.name(), bundle);
        }
        this.knownBundles = Collections.unmodifiableMap(indexed);
        this.skillEnabled = skillEnabled;
        if (!skillEnabled && indexed.values().stream()
                .anyMatch(bundle -> bundle.plugins().contains(UPSTREAM_SKILLS))) {
            startupWarning.accept("Bundle row 'upstream-skills' is waiting because the 'skill' row is disabled");
        }
    }

    public AssembledBundle assemble(BundleDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("bundle definition required");
        }
        BundleDefinition resolved = knownBundles.getOrDefault(definition.name(), definition);
        List<BundleRow> rows = resolved.plugins().stream()
                .map(plugin -> new BundleRow(plugin,
                        !skillEnabled && UPSTREAM_SKILLS.equals(plugin)
                                ? RowStatus.WAITING : RowStatus.ACTIVE))
                .toList();
        return new AssembledBundle(resolved.name(), resolved.plugins(), resolved.config(), rows);
    }

    public AssembledBundle assembleByName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("bundle name required");
        }
        BundleDefinition definition = knownBundles.get(name);
        if (definition == null) {
            throw new IllegalStateException("unknown bundle: " + name);
        }
        return assemble(definition);
    }

    private static List<BundleDefinition> defaultBundles() {
        return List.of(
                BundleDefinition.BASE,
                BundleDefinition.HEADLESS,
                BundleDefinition.SDK_APP,
                BundleDefinition.SDK_MINIMAL,
                BundleDefinition.ACP_APP,
                BundleDefinition.WEB_APP
        );
    }

    public enum RowStatus {
        ACTIVE,
        WAITING
    }

    public record BundleRow(String name, RowStatus status) { }

    public record AssembledBundle(
            String name,
            List<String> plugins,
            Map<String, String> config,
            List<BundleRow> rows
    ) {
        public AssembledBundle(String name, List<String> plugins, Map<String, String> config) {
            this(name, plugins, config,
                    (plugins == null ? List.<String>of() : plugins).stream()
                            .map(plugin -> new BundleRow(plugin, RowStatus.ACTIVE))
                            .toList());
        }

        public AssembledBundle {
            plugins = plugins == null ? List.of() : List.copyOf(plugins);
            config = config == null ? Map.of() : Map.copyOf(config);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        public List<String> activePlugins() {
            return rows.stream().filter(row -> row.status() == RowStatus.ACTIVE)
                    .map(BundleRow::name).toList();
        }
    }
}
