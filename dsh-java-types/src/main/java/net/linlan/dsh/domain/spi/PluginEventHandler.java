package net.linlan.dsh.domain.spi;

/**
 * 匹配事件发布到插件总线时执行的回调。
 * <p>处理器应快速返回且不阻塞调用线程；耗时工作应由实现方转交线程池或队列处理。
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 */
@FunctionalInterface
public interface PluginEventHandler {

    /**
     * 处理一个插件事件。
     *
     * @param event 不可变事件信封
     */
    void handle(PluginEvent event);
}
