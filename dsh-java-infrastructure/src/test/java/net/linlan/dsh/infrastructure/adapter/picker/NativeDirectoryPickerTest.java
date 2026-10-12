package net.linlan.dsh.infrastructure.adapter.picker;

import net.linlan.dsh.domain.tool.picker.adapter.port.IDirectoryPickerPort;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;

import static org.junit.jupiter.api.Assertions.*;

class NativeDirectoryPickerTest {

    @Test
    void implementsDirectoryPickerPort() {
        NativeDirectoryPicker picker = new NativeDirectoryPicker();
        assertTrue(picker instanceof IDirectoryPickerPort);
    }

    @Test
    void pickNullThrowsNpe() {
        NativeDirectoryPicker picker = new NativeDirectoryPicker();
        assertThrows(NullPointerException.class, () -> picker.pick(null));
    }

    @Test
    void pickThrowsHeadlessExceptionInHeadlessEnvironment() {
        org.junit.jupiter.api.Assumptions.assumeTrue(GraphicsEnvironment.isHeadless());
        NativeDirectoryPicker picker = new NativeDirectoryPicker();
        assertThrows(HeadlessException.class, () -> picker.pick("/tmp"));
    }
}
