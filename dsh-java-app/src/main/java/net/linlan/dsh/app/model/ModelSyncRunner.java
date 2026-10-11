package net.linlan.dsh.app.model;

import net.linlan.dsh.domain.runtime.setting.IModelSettingCommandService;
import net.linlan.dsh.domain.runtime.setting.IModelSettingQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 启动时按需初始化默认模型配置。
 * <p>application.yml 中的模型只作为首次启动的种子数据；数据库中已存在 default 渠道配置时，
 * 运行时以数据库为准，避免重启覆盖控制台保存的模型设置。
 * <p>调用流程：Spring Boot 启动 → 加载模型/插件预设 → 注册运行时组件。</p>
 * <p>示例：启动器同步默认模型并装载预置插件。</p>
 */
@Component
public class ModelSyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModelSyncRunner.class);

    private static final String DEFAULT_CHANNEL = "default";

    private final IModelSettingQueryService queryService;
    private final IModelSettingCommandService commandService;
    private final String defaultModel;
    private final String baseUrl;
    private final ApplicationContext applicationContext;

    /**
     * 初始化默认模型同步器。
     * <p>
     * 流程：读取启动配置 → 注入模型设置读写服务 → 供应用启动时执行同步。
     *
     * @param queryService 模型设置查询服务。
     * @param commandService 模型设置命令服务。
     * @param defaultModel 配置的默认模型编码。
     * @param baseUrl 模型提供方基础地址。
     * @param applicationContext Spring 应用上下文，用于记录启动环境。
     */
    public ModelSyncRunner(
            IModelSettingQueryService queryService,
            IModelSettingCommandService commandService,
            @Value("${harness.llm.deepseek.default-model:${LLM_DEFAULT_MODEL:${DEEPSEEK_DEFAULT_MODEL:Deepseek-V4.1-Flash}}}") String defaultModel,
            @Value("${harness.llm.deepseek.base-url:${LLM_BASE_URL:${DEEPSEEK_BASE_URL:http://127.0.0.1:28080/v1}}}") String baseUrl,
            ApplicationContext applicationContext
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.defaultModel = defaultModel;
        this.baseUrl = baseUrl;
        this.applicationContext = applicationContext;
    }

    @Override
    /**
     * 应用启动后按需写入默认模型种子。
     * <p>
     * 流程：查询 default 渠道 -> 已存在则直接返回 -> 不存在则用文件配置初始化；失败仅记录日志，不阻断启动。
     * <p>
     * 示例：配置默认模型为 Deepseek-V4.1-Flash，首次启动后模型设置接口返回该模型。
     *
     * @param args Spring Boot 启动参数。
     */
    public void run(ApplicationArguments args) {
        try {
            var existing = queryService.findByChannelCode(DEFAULT_CHANNEL);
            if (existing.isPresent()) {
                return;
            }
            commandService.save(DEFAULT_CHANNEL, "默认渠道", "deepseek", defaultModel, baseUrl, "", "openai", true);
            log.info("Model bootstrap: seeded default model '{}' for channel '{}'", defaultModel, DEFAULT_CHANNEL);
        } catch (Exception e) {
            log.error("Model sync failed: {}", e.getMessage(), e);
        }
    }
}
