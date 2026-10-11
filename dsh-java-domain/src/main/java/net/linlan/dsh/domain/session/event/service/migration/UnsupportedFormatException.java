package net.linlan.dsh.domain.session.event.service.migration;

import net.linlan.dsh.domain.session.event.model.valobj.SessionFormatVersion;

/**
 * 当会话格式版本超过当前支持的版本时抛出。
 */
public final class UnsupportedFormatException extends RuntimeException {

    private final int detectedVersion;

    public UnsupportedFormatException(int detectedVersion) {
        super("Unsupported session format version: " + detectedVersion
                + ", current supported: " + SessionFormatVersion.CURRENT);
        this.detectedVersion = detectedVersion;
    }

    public int detectedVersion() {
        return detectedVersion;
    }
}
