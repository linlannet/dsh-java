package net.linlan.dsh.domain.host.picker.adapter.port;

import java.util.Optional;

/**
 * IDirectoryPickerBackendPort 目录选择后端出站端口。
 *
 */
public interface IDirectoryPickerBackendPort {

    boolean supports();

    Optional<String> pick(String title, String initialPath);
}
