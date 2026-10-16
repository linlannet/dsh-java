package net.linlan.dsh.domain.host.picker.service;

import net.linlan.dsh.domain.host.picker.adapter.port.IDirectoryPickerBackendPort;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * DirectoryPickerSeam 定义工作区目录选择的能力约定与错误词汇，支持多种后端注册。
 *
 */
@Service
public class DirectoryPickerSeam {

    private final List<IDirectoryPickerBackendPort> backends;

    public DirectoryPickerSeam(List<IDirectoryPickerBackendPort> backends) {
        this.backends = Objects.requireNonNull(backends, "backends 不能为空");
    }

    public Optional<String> pick(PickerMode mode, String title, String initialPath) {
        Objects.requireNonNull(mode, "mode 不能为空");
        for (IDirectoryPickerBackendPort backend : backends) {
            if (backend.supports()) {
                Optional<String> result = backend.pick(title, initialPath);
                if (result.isPresent()) {
                    return result;
                }
            }
        }
        return Optional.empty();
    }

    public List<String> availableBackends() {
        return backends.stream()
                .filter(IDirectoryPickerBackendPort::supports)
                .map(Object::toString)
                .toList();
    }
}
