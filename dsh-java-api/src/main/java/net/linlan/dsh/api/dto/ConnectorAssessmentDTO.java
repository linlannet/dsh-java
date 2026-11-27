package net.linlan.dsh.api.dto;

import java.util.List;
import java.util.Map;

/** 连接器评估及插件页品牌、多语言与功能预览。 */
public record ConnectorAssessmentDTO(
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
}
