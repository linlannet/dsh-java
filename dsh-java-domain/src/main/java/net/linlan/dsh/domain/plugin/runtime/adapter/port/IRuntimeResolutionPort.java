package net.linlan.dsh.domain.plugin.runtime.adapter.port;

import net.linlan.dsh.domain.plugin.runtime.model.valobj.ResolvedPackage;

import java.util.Map;
import java.util.Optional;

/**
 * IRuntimeResolutionPort 是运行时插件解析端口。
 * <p>domain 定义端口契约，infrastructure 提供实现。</p>
 */
public interface IRuntimeResolutionPort {

    /**
     * 返回当前全部解析映射的快照。
     *
     * @return moduleId → ResolvedPackage 映射
     */
    Map<String, ResolvedPackage> resolutionMap();

    /**
     * 刷新指定模块的解析结果。
     *
     * @param moduleId 模块标识
     */
    void refresh(String moduleId);

    /**
     * 解析指定模块。
     *
     * @param moduleId 模块标识
     * @return 解析结果，不存在返回空
     */
    Optional<ResolvedPackage> resolve(String moduleId);

    /**
     * 注册或更新一个已解析的包。
     *
     * @param pkg 已解析的包
     */
    void register(ResolvedPackage pkg);
}
