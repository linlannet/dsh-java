package net.linlan.dsh.domain.jobs.service;

import net.linlan.dsh.domain.jobs.IJobRegistryService;
import net.linlan.dsh.domain.jobs.adapter.repository.IJobRegistryRepository;
import net.linlan.dsh.domain.jobs.model.entity.JobRecord;
import net.linlan.dsh.domain.jobs.model.entity.JobStart;
import net.linlan.dsh.domain.jobs.model.valobj.JobOutcome;
import net.linlan.dsh.domain.jobs.model.valobj.JobSnapshot;
import net.linlan.dsh.domain.jobs.model.valobj.JobStatus;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * In-process job registry. Tracks running jobs, cancels on disposal, and
 * delegates execution to a virtual-thread pool.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class JobRegistryService implements IJobRegistryService {

    private final IJobRegistryRepository repository;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, JobRecord> liveJobs = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private volatile boolean closed = false;

    /**
     * 处理后台作业 Registry Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param repository repository参数。
     * @return 方法执行结果。
     */
    public JobRegistryService(IJobRegistryRepository repository) {
        this.repository = repository;
    }

    @Override
    public JobSnapshot start(JobStart start) {
        if (closed) throw new IllegalStateException("job registry is closed");
        String id = start.kind().name().toLowerCase() + "_" + idCounter.incrementAndGet();
        JobRecord record = new JobRecord(id, start.kind(), start.label(), start.ownerSessionId());
        repository.save(record);
        liveJobs.put(id, record);

        CompletableFuture.runAsync(() -> {
            if (closed) return;
            try {
                JobOutcome outcome = start.work().call();
                record.complete(outcome);
            } catch (Exception e) {
                record.complete(new JobOutcome(JobStatus.FAILED, e.getClass().getSimpleName() + ": " + e.getMessage(), null));
            } finally {
                repository.save(record);
                liveJobs.remove(id);
            }
        }, executor);

        return JobSnapshot.from(record);
    }
    /**
     * 读取目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param id id参数。
     * @param requestingSessionId requesting Session Id参数。
     * @return 方法执行结果。
     */


    @Override
    public Optional<JobSnapshot> read(String id, String requestingSessionId) {
        return repository.findById(id)
                .filter(r -> r.ownerSessionId() == null || r.ownerSessionId().equals(requestingSessionId))
    /**
     * 列出。
     */
                .map(JobSnapshot::from);
    }

    @Override
    public List<JobSnapshot> list(String requestingSessionId) {
        return repository.findByOwnerSessionId(requestingSessionId).stream()
                .map(JobSnapshot::from)
                .toList();
    }

    @Override
    public boolean cancel(String id, String requestingSessionId, String reason) {
        JobRecord record = liveJobs.get(id);
        if (record == null) return false;
        if (record.ownerSessionId() != null && !requestingSessionId.equals(record.ownerSessionId())) return false;
        record.complete(new JobOutcome(JobStatus.KILLED, reason, null));
        liveJobs.remove(id);
        repository.save(record);
        return true;
    }

    /**
     * 释放资源并关闭关联连接。
     */
    public void close() {
        closed = true;
        executor.shutdownNow();
    }
}
