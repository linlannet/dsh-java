package net.linlan.dsh.infrastructure.adapter.picker;

import net.linlan.dsh.domain.tool.picker.adapter.port.IDirectoryPickerPort;

import java.awt.FileDialog;
import java.io.File;
import java.util.Objects;

public final class NativeDirectoryPicker implements IDirectoryPickerPort {

    @Override
    public String pick(String startPath) {
        Objects.requireNonNull(startPath, "startPath");
        FileDialog dialog = new FileDialog((java.awt.Frame) null, "Select directory", FileDialog.LOAD);
        try {
            File start = new File(startPath);
            if (start.isDirectory()) {
                dialog.setDirectory(start.getAbsolutePath());
            }
            dialog.setVisible(true);
            String dir = dialog.getDirectory();
            String file = dialog.getFile();
            if (dir == null) {
                return null;
            }
            if (file != null) {
                File selected = new File(dir, file);
                return selected.isDirectory() ? selected.getAbsolutePath() : dir;
            }
            return dir;
        } finally {
            dialog.dispose();
        }
    }
}
