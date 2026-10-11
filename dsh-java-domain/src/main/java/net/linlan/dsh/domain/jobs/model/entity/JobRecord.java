package net.linlan.dsh.domain.jobs.model.entity;
import net.linlan.dsh.domain.jobs.model.valobj.JobStatus;


import net.linlan.dsh.domain.jobs.model.valobj.JobKind;
import net.linlan.dsh.domain.jobs.model.valobj.JobOutcome;
import java.time.Instant;

/**
 * JobRecord 表示JobRecord。
 */
public class JobRecord {
    private final String id;
    private final JobKind kind;
    private final String label;
    private final String ownerSessionId;
    private volatile JobStatus status;
    private volatile String detail;
    private volatile String output;
    private final Instant startedAt = Instant.now();
    private volatile Instant finishedAt;
    private volatile JobOutcome outcome;

    /**
     * 处理后台作业 Record。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param id ID参数。
     * @param kind kind参数。
     * @param label label参数。
     * @param ownerSessionId owner 会话 ID参数。
     * @return 方法执行结果。
     */
    public JobRecord(String id, JobKind kind, String label, String ownerSessionId) {
        this.id = id;
        this.kind = kind;
        this.label = label;
        this.ownerSessionId = ownerSessionId;
        this.status = JobStatus.RUNNING;
    }

    /**
     * 处理complete。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param outcome outcome参数。
     */
    public void complete(JobOutcome outcome) {
        this.outcome = outcome;
        this.status = outcome.status();
        this.detail = outcome.detail();
        this.output = outcome.output();
        this.finishedAt = Instant.now();
    }

    /**
     * 处理outcome。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public JobOutcome outcome() { return outcome; }
    /**
     * 处理status。
     * @return 方法执行结果。
     */
    public JobStatus status() { return status; }
    /**
     * 处理ID。
     * @return 方法执行结果。
     */
    public String id() { return id; }
    /**
     * 处理kind。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public JobKind kind() { return kind; }
    /**
     * 处理label。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public String label() { return label; }
    /**
     * 处理owner 会话 ID。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public String ownerSessionId() { return ownerSessionId; }
    /**
     * 处理detail。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public String detail() { return detail; }
    /**
     * 处理output。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public String output() { return output; }
    /**
     * 启动 ed At。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public Instant startedAt() { return startedAt; }
    /**
     * 处理finished At。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public Instant finishedAt() { return finishedAt; }
}
