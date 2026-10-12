package net.linlan.dsh.domain.runtime.profile.bundle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BundleAssembler 按 BundleDefinition 装配插件集与配置。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public class BundleAssembler {

    private final Map<String, BundleDefinition> knownBundles;

    public BundleAssembler() {
        this(List.of(
                BundleDefinition.BASE,
                BundleDefinition.HEADLESS,
                BundleDefinition.SDK_APP,
                BundleDefinition.SDK_MINIMAL,
                BundleDefinition.ACP_APP,
                BundleDefinition.WEB_APP
        ));
    }

    public BundleAssembler(List<BundleDefinition> bundles) {
        Map<String, BundleDefinition> indexed = new LinkedHashMap<>();
        for (BundleDefinition b : bundles) {
            indexed.put(b.name(), b);
        }
        this.knownBundles = Map.copyOf(indexed);
    }

    public AssembledBundle assemble(BundleDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("bundle definition required");
        }
        BundleDefinition resolved = knownBundles.getOrDefault(definition.name(), definition);
        return new AssembledBundle(resolved.name(), resolved.plugins(), resolved.config());
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

    public record AssembledBundle(
            String name,
            List<String> plugins,
            Map<String, String> config
    ) {
        public AssembledBundle {
            plugins = plugins == null ? List.of() : List.copyOf(plugins);
            config = config == null ? Map.of() : Map.copyOf(config);
        }
    }
}
