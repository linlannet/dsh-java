package net.linlan.dsh.infrastructure.adapter.picker;

import net.linlan.dsh.domain.host.picker.adapter.port.IDirectoryPickerBackendPort;

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * NativeDirectoryPickerBackend 原生 OS 选择器后端。
 *
 * <p>面向宿主屏幕前操作者弹出原生 OS 对话框；无图形环境（headless）时不可用。</p>
 *
 */
@Component
@Order(1)
public class NativeDirectoryPickerBackend implements IDirectoryPickerBackendPort {

    @Override
    public boolean supports() {
        return !GraphicsEnvironment.isHeadless();
    }

    @Override
    public Optional<String> pick(String title, String initialPath) {
        if (GraphicsEnvironment.isHeadless()) {
            return Optional.empty();
        }
        FileDialog dialog = new FileDialog((Frame) null, title == null ? "Select directory" : title, FileDialog.LOAD);
        try {
            if (initialPath != null && !initialPath.isBlank()) {
                File start = new File(initialPath);
                if (start.isDirectory()) {
                    dialog.setDirectory(start.getAbsolutePath());
                }
            }
            dialog.setVisible(true);
            String dir = dialog.getDirectory();
            if (dir == null) {
                return Optional.empty();
            }
            String file = dialog.getFile();
            File selected = file == null ? new File(dir) : new File(dir, file);
            return Optional.of(selected.isDirectory() ? selected.getAbsolutePath() : dir);
        } finally {
            dialog.dispose();
        }
    }
}