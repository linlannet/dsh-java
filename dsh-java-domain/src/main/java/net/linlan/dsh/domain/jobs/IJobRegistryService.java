package net.linlan.dsh.domain.jobs;

import net.linlan.dsh.domain.jobs.model.entity.JobStart;
import net.linlan.dsh.domain.jobs.model.valobj.JobSnapshot;
import java.util.List;
import java.util.Optional;

/**
 * Background-job capability seam. Start, read, list, and cancel jobs.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IJobRegistryService {

    JobSnapshot start(JobStart start);

    Optional<JobSnapshot> read(String id, String requestingSessionId);

    List<JobSnapshot> list(String requestingSessionId);

    boolean cancel(String id, String requestingSessionId, String reason);
}
