package net.linlan.dsh.domain.hooks.model.valobj;

import java.util.List;

/**
 * MatcherGroup 表示MatcherGroup。
 */
public record MatcherGroup(String matcher, List<CommandHook> hooks) {
    public MatcherGroup {
        hooks = hooks == null ? List.of() : List.copyOf(hooks);
    }
}
