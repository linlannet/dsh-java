package net.linlan.dsh.domain.hooks.service;
import java.util.concurrent.ConcurrentHashMap;


import net.linlan.dsh.domain.hooks.IHookService;
import net.linlan.dsh.domain.hooks.adapter.port.IHookRunnerPort;
import net.linlan.dsh.domain.hooks.model.valobj.*;

import java.util.*;
import org.springframework.stereotype.Service;

/**
 * HookService 封装Hook的领域规则与流程。
 */
@Service
public class HookService implements IHookService {

    private final IHookRunnerPort runnerPort;
    private final Map<HookDialect, Map<HookPoint, List<MatcherGroup>>> config = new ConcurrentHashMap<>();

    /**
     * 处理Hook Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param runnerPort runner Port参数。
     * @return 方法执行结果。
     */
    public HookService(IHookRunnerPort runnerPort) {
        this.runnerPort = runnerPort;
    }

    /**
     * 注册 Matchers。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param dialect dialect参数。
     * @param point point参数。
     * @param groups groups参数。
     */
    public void registerMatchers(HookDialect dialect, HookPoint point, List<MatcherGroup> groups) {
        config.computeIfAbsent(dialect, d -> new EnumMap<>(HookPoint.class))
                .put(point, List.copyOf(groups));
    }

    @Override
    public MergedHookOutcome runHooks(HookDialect dialect, HookPoint point, String toolName,
                                      String payloadJson, Map<String, String> environment) {
        Map<HookPoint, List<MatcherGroup>> dialectConfig = config.get(dialect);
        if (dialectConfig == null) return emptyOutcome();
        List<MatcherGroup> groups = dialectConfig.get(point);
        if (groups == null || groups.isEmpty()) return emptyOutcome();

        List<MatcherGroup> matching = HookMatcher.filterMatching(dialect, groups, toolName);
        if (matching.isEmpty()) return emptyOutcome();

        List<HookOutput> outputs = new ArrayList<>();
        for (MatcherGroup group : matching) {
            for (CommandHook hook : group.hooks()) {
                HookOutput output = runnerPort.run(hook, payloadJson, environment);
                outputs.add(output);
            }
        }
        return HookOutputMerger.merge(outputs);
    }

    /**
     * 处理empty Outcome。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    private static MergedHookOutcome emptyOutcome() {
        return new MergedHookOutcome(Optional.empty(), "", "", false, Optional.empty());
    }
}
