package net.linlan.dsh.domain.typert.ptc.python.model.valobj;

public record ResourceBudget(int cpuLimit, int memoryLimitMb, long timeLimitMs) {
    public ResourceBudget {
        cpuLimit = cpuLimit <= 0 ? 1 : cpuLimit;
        memoryLimitMb = memoryLimitMb <= 0 ? 256 : memoryLimitMb;
        timeLimitMs = timeLimitMs <= 0 ? 30000 : timeLimitMs;
    }
    public static ResourceBudget defaults() { return new ResourceBudget(1, 256, 30000); }
}
