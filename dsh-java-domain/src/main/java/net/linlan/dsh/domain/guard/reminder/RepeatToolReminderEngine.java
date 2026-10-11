package net.linlan.dsh.domain.guard.reminder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Advisory per-agent repeat-call detector. It counts consecutive identical
 * tool calls and injects reminders at configured thresholds.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class RepeatToolReminderEngine {

    private final List<Integer> thresholds;
    private final Set<Integer> thresholdSet;
    private final int argumentsPreviewChars;
    private final Map<String, Chain> chains = new ConcurrentHashMap<>();

    /**
     * 构造RepeatToolReminderEngine领域对象。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param thresholds threshold列表。
     */
    public RepeatToolReminderEngine(List<Integer> thresholds, int argumentsPreviewChars) {
        if (thresholds == null || thresholds.isEmpty()) {
            throw new IllegalArgumentException("thresholds must not be empty");
        }
        Set<Integer> set = new LinkedHashSet<>(thresholds);
        for (int t : thresholds) {
            if (t < 2) throw new IllegalArgumentException("thresholds must be >= 2, got: " + t);
        }
        if (set.size() != thresholds.size()) throw new IllegalArgumentException("thresholds must have no duplicates");
        this.thresholds = thresholds.stream().sorted().toList();
        this.thresholdSet = set;
        this.argumentsPreviewChars = Math.max(1, argumentsPreviewChars);
    }

    @Autowired
    public RepeatToolReminderEngine() {
        this(Arrays.asList(3, 5, 8), 500);
    }

    /**
     * 处理observe。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param agentId 智能体ID。
     * @param arguments 参数。
     * @return 领域处理结果。
     */
    public Optional<String> observe(String agentId, String toolName, String arguments) {
        String canonical = arguments == null ? "" : arguments;
        String compositeKey = toolName + "||" + canonical;
        Chain chain = chains.get(agentId);
        int count = (chain != null && chain.key.equals(compositeKey)) ? chain.count + 1 : 1;
        chains.put(agentId, new Chain(compositeKey, count));

        if (!thresholdSet.contains(count)) return Optional.empty();

        String preview = previewArguments(canonical);
        if (count == thresholds.get(0)) {
            return Optional.of("You are repeating the exact same tool call with identical arguments. "
                    + "Carefully analyze the previous result before calling again: if the task is not complete, "
                    + "try a different approach or different arguments instead of repeating the call.");
        } else {
            return Optional.of("Repeated tool call detected:\n"
                    + "- tool: " + toolName + "\n"
                    + "- consecutive_calls: " + count + "\n"
                    + "- arguments: " + preview + "\n"
                    + "The repeated calls are not making progress. Do not call this tool with "
                    + "these exact arguments again. Inspect the latest result and choose a "
                    + "different action, different arguments, or finish the task if enough "
                    + "evidence has been gathered.");
        }
    }

    /**
     * 处理reset。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param agentId Agent ID。
     */
    public void reset(String agentId) {
        chains.remove(agentId);
    }

    /**
     * 处理preview Arguments。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param arguments arguments参数。
     * @return 方法执行结果。
     */
    private String previewArguments(String arguments) {
        if (arguments == null || arguments.isEmpty()) return "";
        int max = Math.min(arguments.length(), argumentsPreviewChars);
        return arguments.substring(0, max) + (arguments.length() > max ? "…" : "");
    }

    /**
     * Chain 定义当前模块中的record契约与职责。
     */
    private record Chain(String key, int count) {}
}
