package net.linlan.dsh.domain.host.openinapp.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * OpenInAppService 提供在已安装应用中打开 workspace 目录的应用探测与启动路由。
 *
 * <p>未检测到应用时返回明确提示。</p>
 *
 */
@Service
public class OpenInAppService {

    public boolean detectInstalled(String appName) {
        Objects.requireNonNull(appName, "appName 不能为空");
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (new java.io.File(dir, appName).exists()) {
                return true;
            }
        }
        return false;
    }

    public Optional<String> launch(String appName, String target) {
        Objects.requireNonNull(appName, "appName 不能为空");
        Objects.requireNonNull(target, "target 不能为空");
        if (!detectInstalled(appName)) {
            return Optional.empty();
        }
        return Optional.of(appName + " " + target);
    }

    public List<String> detectAll(List<String> appNames) {
        return appNames.stream()
                .filter(this::detectInstalled)
                .toList();
    }
}
