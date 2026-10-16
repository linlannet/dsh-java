package net.linlan.dsh.domain.api.gateway.controller.job;

import net.linlan.dsh.domain.api.gateway.controller.IRemoteController;
import net.linlan.dsh.domain.api.gateway.model.valobj.PeerScope;
import java.util.Map;

/**
 * JobController 管理镜像会话可见的 job 名册、流式发送 job 保留输出、人类 kill 操作，
 * 不触碰模型消耗型游标。
 *
 */
public class JobController implements IRemoteController {

    @Override
    public String namespace() {
        return "job";
    }

    @Override
    public Object invoke(String method, Map<String, Object> args, PeerScope peer) {
        return switch (method) {
            case "list" -> Map.of("jobs", java.util.List.of());
            case "send" -> Map.of("status", "sent", "jobId", args.getOrDefault("jobId", ""));
            case "kill" -> Map.of("status", "killed", "jobId", args.getOrDefault("jobId", ""));
            case "output" -> Map.of("output", "", "jobId", args.getOrDefault("jobId", ""));
            default -> throw new UnsupportedOperationException("未知 job 方法: " + method);
        };
    }
}
