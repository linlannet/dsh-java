package net.linlan.dsh.domain.typert.generator.adapter.port;

/**
 * ITypertGeneratorPort 定义类型协议生成器的领域端口，由基础设施层适配。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public interface ITypertGeneratorPort {

    /**
     * 从 TypeScript 项目路径生成类型协议定义。
     *
     * @param tsProjectPath TypeScript 项目路径
     * @return 类型协议定义字符串
     */
    String generate(String tsProjectPath);
}
