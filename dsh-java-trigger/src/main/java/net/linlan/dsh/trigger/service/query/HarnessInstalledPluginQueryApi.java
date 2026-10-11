package net.linlan.dsh.trigger.service.query;

import net.linlan.dsh.api.dto.InstalledPluginResponseDTO;
import net.linlan.dsh.api.IHarnessInstalledPluginQueryApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.plugin.IInstalledPluginQueryCase;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 已安装插件查询适配器。
 * <p>
 * 执行流程：
 * 1. 从插件仓储读取插件实体；
 * 2. 将实体转换为前端展示对象；
 * 3. 返回给控制器列表展示。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
@Service
public class HarnessInstalledPluginQueryApi implements IHarnessInstalledPluginQueryApi {

    private final IInstalledPluginQueryCase installedPluginQueryCase;

    /**
     * 处理Harness Installed 插件 Query API。
     * 流程：转换请求 → 调用用例或领域服务 → 转换响应。
     * @param harnessPluginRepository harness 插件 Repository参数。
     * @return 方法执行结果。
     */
    public HarnessInstalledPluginQueryApi(IInstalledPluginQueryCase installedPluginQueryCase) {
        this.installedPluginQueryCase = installedPluginQueryCase;
    }

    /**
     * 查询已安装插件列表。
     * <p>
     * 执行流程：
     * 1. 查询仓储中的全部插件；
     * 2. 映射为 `InstalledPluginResponseDTO`；
     * 3. 返回给上层页面。
     */
    public Response<List<InstalledPluginResponseDTO>> listInstalledPlugins() {
        return execute(installedPluginQueryCase::listInstalledPlugins);
    }

    private <T> Response<T> execute(java.util.function.Supplier<T> action) {
        try {
            return Response.success(action.get());
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }
}
