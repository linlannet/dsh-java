/**
 * 子代理服务。
 *
 * <p>该包提供子任务委派的提供者、注册中心和工具适配；子代理本身复用 {@code agent.run}
 * 创建独立会话与运行生命周期，避免在父 Agent 上下文中共享执行状态。</p>
 */
package net.linlan.dsh.domain.agent.service.subagent;
