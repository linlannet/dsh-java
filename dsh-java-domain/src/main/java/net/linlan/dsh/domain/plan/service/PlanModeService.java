package net.linlan.dsh.domain.plan.service;

import net.linlan.dsh.domain.plan.model.valobj.PlanModeState;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

/**
 * 计划模式领域服务。
 * <p>
 * 执行流程：
 * 1. 写入计划模式变更事件；
 * 2. 更新内存态计划状态；
 * 3. 从会话事件回放计划模式。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class PlanModeService {

    /**
     * 进入计划模式。
     * <p>
     * 执行流程：
     * 1. 记录 `plan/mode` 的 enter 事件；
     * 2. 切换状态为 planning；
     * 3. 返回当前状态引用。
     */
    public void enterPlanMode(SessionLog session, AtomicReference<PlanModeState> state) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        session.append(SessionEventFactory.planModeEnter());
        state.set(new PlanModeState.Planning());
    }

    /**
     * 提交计划并退出计划模式。
     * <p>
     * 执行流程：
     * 1. 校验计划文本；
     * 2. 记录 `plan/mode` 的 exit 事件；
     * 3. 更新状态为 approved。
     */
    public void submitPlan(SessionLog session, AtomicReference<PlanModeState> state, String plan) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (plan == null || plan.isBlank()) {
            throw new IllegalArgumentException("plan must not be blank");
        }
        session.append(SessionEventFactory.planModeExit(plan));
        state.set(new PlanModeState.Approved(plan));
    }

    /**
     * 从会话事件折叠出当前计划模式。
     * <p>
     * 执行流程：
     * 1. 遍历会话事件；
     * 2. 读取最后一次 plan/mode 变更；
     * 3. 返回折叠后的状态。
     */
    public PlanModeState foldState(SessionLog session) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        PlanModeState state = new PlanModeState.Off();
        for (SessionEvent event : session.events()) {
            if (event instanceof SessionEvent.PlanModeChange pm) {
                if ("enter".equals(pm.data().mode())) {
                    state = new PlanModeState.Planning();
                } else if ("exit".equals(pm.data().mode())) {
                    state = new PlanModeState.Approved(pm.data().plan());
                }
            }
        }
        return state;
    }
}
