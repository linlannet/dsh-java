package net.linlan.dsh.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 启动 dsh 的 Java 版工程。
 *
 * <p>调用流程：Spring Boot 启动 → 加载模型/插件预设 → 注册运行时组件。</p>
 * <p>示例：启动器同步默认模型并装载预置插件。</p>
 *
 */
@SpringBootApplication(scanBasePackages = "net.linlan.dsh")
@MapperScan("net.linlan.dsh.infrastructure.dao")
public class Application {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
