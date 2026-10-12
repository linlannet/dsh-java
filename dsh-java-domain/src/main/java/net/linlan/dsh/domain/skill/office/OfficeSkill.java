package net.linlan.dsh.domain.skill.office;

import net.linlan.dsh.domain.skill.adapter.port.ISkillProviderPort;
import net.linlan.dsh.domain.skill.model.entity.SkillCandidate;
import net.linlan.dsh.domain.skill.model.entity.SkillDefinition;
import net.linlan.dsh.domain.skill.model.valobj.SkillInvocationPolicy;
import net.linlan.dsh.domain.skill.model.valobj.SkillSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class OfficeSkill implements ISkillProviderPort {

    private static final String SKILL_NAME = "office";
    private static final String SKILL_DESCRIPTION = "Office workflow skill: open, edit, convert documents.";

    private final Map<String, String> openedDocuments;
    private final List<String> operationsLog;

    public OfficeSkill() {
        this.openedDocuments = new java.util.concurrent.ConcurrentHashMap<>();
        this.operationsLog = new java.util.concurrent.CopyOnWriteArrayList<>();
    }

    public String open(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path required");
        }
        String normalized = Path.of(path).toString();
        openedDocuments.put(normalized, "");
        operationsLog.add("open:" + normalized);
        return normalized;
    }

    public String edit(String path, Map<String, Object> changes) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path required");
        }
        if (changes == null) {
            throw new IllegalArgumentException("changes required");
        }
        String normalized = Path.of(path).toString();
        if (!openedDocuments.containsKey(normalized)) {
            openedDocuments.put(normalized, "");
        }
        String current = openedDocuments.get(normalized);
        String updated = applyChanges(current, changes);
        openedDocuments.put(normalized, updated);
        operationsLog.add("edit:" + normalized);
        return updated;
    }

    public String convert(String path, String format) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path required");
        }
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("format required");
        }
        String normalized = Path.of(path).toString();
        String content = openedDocuments.getOrDefault(normalized, "");
        operationsLog.add("convert:" + normalized + ":" + format);
        return content + "[format:" + format + "]";
    }

    public Optional<String> getContent(String path) {
        if (path == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(openedDocuments.get(Path.of(path).toString()));
    }

    public List<String> operationsLog() {
        return List.copyOf(operationsLog);
    }

    private String applyChanges(String current, Map<String, Object> changes) {
        StringBuilder sb = new StringBuilder(current);
        for (var entry : changes.entrySet()) {
            sb.append(entry.getKey()).append("=").append(Objects.toString(entry.getValue(), "")).append(";");
        }
        return sb.toString();
    }

    @Override
    public List<SkillCandidate> discover(String cwd) {
        SkillCandidate candidate = new SkillCandidate(
                SKILL_NAME,
                SKILL_DESCRIPTION,
                Optional.of("When working with office documents"),
                SkillInvocationPolicy.both(),
                SkillSource.BUNDLED,
                "office",
                Optional.empty(),
                0,
                "office-skill"
        );
        return List.of(candidate);
    }

    @Override
    public Optional<SkillDefinition> load(SkillCandidate candidate) {
        if (candidate == null || !SKILL_NAME.equals(candidate.name())) {
            return Optional.empty();
        }
        SkillDefinition def = new SkillDefinition(
                SKILL_NAME,
                SKILL_DESCRIPTION,
                Optional.of("When working with office documents"),
                SkillInvocationPolicy.both(),
                SkillSource.BUNDLED,
                "office",
                "office skill content",
                Optional.empty()
        );
        return Optional.of(def);
    }
}
