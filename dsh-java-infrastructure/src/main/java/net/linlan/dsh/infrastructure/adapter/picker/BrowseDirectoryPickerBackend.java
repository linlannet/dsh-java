package net.linlan.dsh.infrastructure.adapter.picker;

import net.linlan.dsh.domain.host.picker.adapter.port.IDirectoryPickerBackendPort;

import java.io.File;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * BrowseDirectoryPickerBackend 应用内浏览选择器后端。
 *
 * <p>服务于远程客户端：服务端侧浏览器提供目录浏览能力，实际选择由远程客户端驱动。
 * 无显式选择时回退到给定初始目录。</p>
 *
 */
@Component
@Order(2)
public class BrowseDirectoryPickerBackend implements IDirectoryPickerBackendPort {

    @Override
    public boolean supports() {
        return true;
    }

    @Override
    public Optional<String> pick(String title, String initialPath) {
        if (initialPath == null || initialPath.isBlank()) {
            return Optional.empty();
        }
        File dir = new File(initialPath);
        if (!dir.isDirectory()) {
            return Optional.empty();
        }
        return Optional.of(dir.getAbsolutePath());
    }
}