package net.linlan.dsh.domain.guard.service;

import net.linlan.dsh.domain.guard.IGuardService;
import net.linlan.dsh.domain.guard.model.valobj.GuardDecision;
import net.linlan.dsh.domain.guard.reminder.RepeatToolReminderEngine;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Composes the timeout-policy and repeat-tool-reminder guards into one seam.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class GuardService implements IGuardService {

    private final RepeatToolReminderEngine reminderEngine;

    /**
     * 处理Guard Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param reminderEngine reminder Engine参数。
     * @return 方法执行结果。
     */
    public GuardService(RepeatToolReminderEngine reminderEngine) {
        this.reminderEngine = reminderEngine;
    }

    @Autowired
    public GuardService() {
        this(new RepeatToolReminderEngine());
    }

    @Override
    public GuardDecision postExecute(Optional<String> agentId, String toolName, String arguments, String resultJson) {
        if (agentId.isEmpty()) return GuardDecision.allow();
        Optional<String> reminder = reminderEngine.observe(agentId.get(), toolName, arguments);
        return reminder.map(GuardDecision::allowWithReminder).orElse(GuardDecision.allow());
    }
    /**
     * pre Step。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param agentId Agent ID。
     * @param hasUserMessage has User Message参数。
     */


    @Override
    public void preStep(String agentId, boolean hasUserMessage) {
        if (hasUserMessage) reminderEngine.reset(agentId);
    }
}
