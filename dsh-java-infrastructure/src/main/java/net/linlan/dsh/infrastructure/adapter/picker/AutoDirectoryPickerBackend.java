package net.linlan.dsh.infrastructure.adapter.picker;

import net.linlan.dsh.domain.host.picker.adapter.port.IDirectoryPickerBackendPort;

import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * AutoDirectoryPickerBackend 自适应选择器后端。
 *
 * <p>启动时挂载匹配后端：优先使用原生 OS 选择器，不可用时回退到应用内浏览选择器。</p>
 *
 */
@Component
@Order(3)
public class AutoDirectoryPickerBackend implements IDirectoryPickerBackendPort {

    private final IDirectoryPickerBackendPort nativeBackend;
    private final IDirectoryPickerBackendPort browseBackend;

    public AutoDirectoryPickerBackend(NativeDirectoryPickerBackend nativeBackend,
                                      BrowseDirectoryPickerBackend browseBackend) {
        this.nativeBackend = nativeBackend;
        this.browseBackend = browseBackend;
    }

    @Override
    public boolean supports() {
        return nativeBackend.supports() || browseBackend.supports();
    }

    @Override
    public Optional<String> pick(String title, String initialPath) {
        if (nativeBackend.supports()) {
            Optional<String> result = nativeBackend.pick(title, initialPath);
            if (result.isPresent()) {
                return result;
            }
        }
        return browseBackend.supports() ? browseBackend.pick(title, initialPath) : Optional.empty();
    }
}