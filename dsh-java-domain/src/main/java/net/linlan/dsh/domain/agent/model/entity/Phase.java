package net.linlan.dsh.domain.agent.model.entity;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 智能体驱动的阶段状态机。
 * <p>职责：表达空闲、维护、运行三种阶段，并承载中断与唤醒补偿语义。</p>
 * <p>流程：空闲 → 维护或运行 → 中断/唤醒事件合并 → 回到空闲。</p>
 * <p>示例：运行阶段收到中断后，等待中的唤醒事件会在阶段回到空闲时补发。</p>
 */
public sealed interface Phase
        permits Phase.Idle, Phase.Maintenance, Phase.Running {

    /** 空闲阶段：没有正在执行的工作，记录最近完成轮次。 */
    record Idle(long lastTurn) implements Phase {}

    /** 维护阶段：非对话轮次的维护任务占用智能体。 */
    record Maintenance(
            AtomicBoolean abort,
            long lastTurn,
            AtomicBoolean wakeRequested
    ) implements Phase {
        /**
         * 处理Maintenance。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param lastTurn last Turn参数。
         * @return 方法执行结果。
         */
        public Maintenance(long lastTurn) {
            this(new AtomicBoolean(false), lastTurn, new AtomicBoolean(false));
        }
    }

    /** 运行阶段：驱动器正在推进轮次和步骤。 */
    record Running(
            AtomicBoolean abort,
            long turn,
            long step,
            AtomicBoolean wakeRequested
    ) implements Phase {
        /**
         * 运行ning。
         */
        public Running(long lastTurn) {
            this(new AtomicBoolean(false), lastTurn, 0, new AtomicBoolean(false));
        }
    }
}
