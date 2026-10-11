package net.linlan.dsh.domain.agent.service.run.tool;

import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionInput;
import net.linlan.dsh.domain.model.valobj.ToolExecutionMode;
import net.linlan.dsh.domain.tool.adapter.ToolRegistry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class CompositeToolRegistry implements ToolRegistry {

    private final ToolRegistry localRegistry;
    private final ToolRegistry sharedRegistry;

    public CompositeToolRegistry(ToolRegistry localRegistry, ToolRegistry sharedRegistry) {
        this.localRegistry = localRegistry;
        this.sharedRegistry = sharedRegistry;
    }

    @Override
    public Runnable register(ToolDefinition definition) {
        return localRegistry.register(definition);
    }

    @Override
    public Optional<ToolDefinition> lookup(String name) {
        return localRegistry.lookup(name).or(() -> sharedRegistry.lookup(name));
    }

    @Override
    public Collection<ToolDefinition> all() {
        Map<String, ToolDefinition> merged = new LinkedHashMap<>();
        sharedRegistry.all().forEach(definition -> merged.put(definition.name(), definition));
        localRegistry.all().forEach(definition -> merged.put(definition.name(), definition));
        return merged.values();
    }

    @Override
    public ToolExecutionMode executionMode(ToolExecutionInput input) {
        ToolDefinition definition = lookup(input.name()).orElse(null);
        if (definition == null) {
            return ToolExecutionMode.EXCLUSIVE;
        }
        return definition.isConcurrencySafe(input.arguments())
                ? ToolExecutionMode.PARALLEL
                : ToolExecutionMode.EXCLUSIVE;
    }
}
