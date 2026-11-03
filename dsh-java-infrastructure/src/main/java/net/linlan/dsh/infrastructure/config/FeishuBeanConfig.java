package net.linlan.dsh.infrastructure.config;

import net.linlan.dsh.api.condition.FeishuEnabledCondition;
import net.linlan.dsh.domain.bot.adapter.port.IFeishuInboundHandler;
import net.linlan.dsh.domain.credentials.adapter.port.ICredentialProviderPort;
import net.linlan.dsh.infrastructure.adapter.feishu.FeishuMessagingClient;
import net.linlan.dsh.infrastructure.adapter.feishu.FeishuPairingClient;
import net.linlan.dsh.infrastructure.adapter.feishu.FeishuWsGateway;
import net.linlan.dsh.infrastructure.adapter.repository.FeishuBotRepository;
import net.linlan.dsh.infrastructure.dao.IFeishuBotDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 飞书接入条件装配：仅当 {@code dsh.feishu.enabled}（缺省 true）时装配飞书基础设施 Bean。
 *
 * <p>关闭时（{@code dsh.feishu.enabled=false}）不产出任何飞书基础设施 Bean，应用上下文正常启动。
 */
@Configuration(proxyBeanMethods = false)
@Conditional(FeishuEnabledCondition.class)
public class FeishuBeanConfig {

    /**
     * 飞书 Bot 仓储适配器。
     *
     * @param dao 数据访问对象
     * @return 仓储适配器
     */
    @Bean
    public FeishuBotRepository feishuBotRepository(IFeishuBotDao dao) {
        return new FeishuBotRepository(dao);
    }

    /**
     * 飞书出站消息适配器。
     *
     * @param credentialPort 凭据端口
     * @param objectMapper   JSON 序列化器
     * @return 出站消息适配器
     */
    @Bean
    public FeishuMessagingClient feishuMessagingClient(ICredentialProviderPort credentialPort, ObjectMapper objectMapper) {
        return new FeishuMessagingClient(credentialPort, objectMapper);
    }

    /**
     * 飞书一键配对适配器。
     *
     * @param objectMapper JSON 解析器
     * @return 配对适配器
     */
    @Bean
    public FeishuPairingClient feishuPairingClient(ObjectMapper objectMapper) {
        return new FeishuPairingClient(objectMapper);
    }

    /**
     * 飞书长连接网关（同时注册启动拉起与关闭钩子）。
     *
     * @param dao             数据访问对象
     * @param inboundHandler  入站处理端口（用例层实现）
     * @param messagingClient 出站消息适配器
     * @param credentialPort  凭据端口
     * @param objectMapper    JSON 解析器
     * @return 长连接网关
     */
    @Bean
    public FeishuWsGateway feishuWsGateway(IFeishuBotDao dao, IFeishuInboundHandler inboundHandler,
                                           FeishuMessagingClient messagingClient, ICredentialProviderPort credentialPort,
                                           ObjectMapper objectMapper) {
        return new FeishuWsGateway(dao, inboundHandler, messagingClient, credentialPort, objectMapper);
    }
}