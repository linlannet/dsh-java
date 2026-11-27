package net.linlan.dsh.domain.connector;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 代码化连接器评估与插件页展示条目。 */
public record ConnectorAssessment(
        String connectorId,
        String displayName,
        String upstreamForm,
        String javaStrategy,
        String decision,
        String alternative,
        String brandIcon,
        Map<String, String> localizedNames,
        Map<String, List<String>> featurePreviews
) {
    private static final Set<String> DECISIONS = Set.of("接入", "不接入", "替代实现");
    private static final Set<String> REQUIRED_LOCALES = Set.of("zh-CN", "en-US");

    public ConnectorAssessment {
        Objects.requireNonNull(connectorId, "connectorId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(upstreamForm, "upstreamForm");
        Objects.requireNonNull(javaStrategy, "javaStrategy");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(alternative, "alternative");
        Objects.requireNonNull(brandIcon, "brandIcon");
        Objects.requireNonNull(localizedNames, "localizedNames");
        Objects.requireNonNull(featurePreviews, "featurePreviews");
        if (!DECISIONS.contains(decision)) {
            throw new IllegalArgumentException("unsupported connector assessment decision: " + decision);
        }
        if (!localizedNames.keySet().containsAll(REQUIRED_LOCALES)
                || !featurePreviews.keySet().containsAll(REQUIRED_LOCALES)) {
            throw new IllegalArgumentException("connector presentation requires zh-CN and en-US");
        }
        localizedNames = Map.copyOf(localizedNames);
        featurePreviews = featurePreviews.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
