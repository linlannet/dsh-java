package net.linlan.dsh.domain.tool.picker;

import net.linlan.dsh.domain.tool.picker.adapter.port.IDirectoryPickerPort;

import java.util.Objects;

public final class DirectoryPicker {

    private final IDirectoryPickerPort port;

    public DirectoryPicker(IDirectoryPickerPort port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    public String pick(String startPath) {
        Objects.requireNonNull(startPath, "startPath");
        if (startPath.isBlank()) {
            throw new IllegalArgumentException("startPath must not be blank");
        }
        String result = port.pick(startPath);
        if (result == null || result.isBlank()) {
            throw new IllegalStateException("no directory selected");
        }
        return result;
    }
}
