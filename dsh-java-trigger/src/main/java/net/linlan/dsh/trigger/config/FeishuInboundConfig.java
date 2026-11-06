package net.linlan.dsh.trigger.config;

import net.linlan.dsh.api.condition.FeishuEnabledCondition;
import net.linlan.dsh.cases.bot.FeishuBotInboundHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 飞书入站桥接处理器装配（组合根）。
 *
 * <p>将用例层实现 {@link FeishuBotInboundHandler}（实现领域端口 {@code IFeishuInboundHandler}）
 * 的 Bean 接入应用上下文。装配发生在触发层而非基础设施层，以保持分层清晰：
 * 基础设施层只消费端口，不反向依赖用例层。
 *
 * <p>整体受 {@code dsh.feishu.enabled}（缺省 true）控制，飞书开关关闭时不产出该 Bean。
 */
@Configuration(proxyBeanMethods = false)
@Conditional(FeishuEnabledCondition.class)
public class FeishuInboundConfig {

    @Bean
    public FeishuBotInboundHandler feishuInboundHandler() {
        return new FeishuBotInboundHandler();
    }
}
