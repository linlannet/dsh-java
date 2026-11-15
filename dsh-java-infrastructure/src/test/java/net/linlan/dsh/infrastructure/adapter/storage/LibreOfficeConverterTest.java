package net.linlan.dsh.infrastructure.adapter.storage;

import net.linlan.dsh.domain.document.office.model.error.OfficeKitError;
import net.linlan.dsh.domain.storage.conversion.adapter.port.IOfficeConverterPort;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LibreOfficeConverterTest {

    @Test
    void implementsOfficeConverterPort() {
        LibreOfficeConverter c = new LibreOfficeConverter();
        assertTrue(c instanceof IOfficeConverterPort);
    }

    @Test
    void defaultConstructor() {
        LibreOfficeConverter c = new LibreOfficeConverter();
        assertNotNull(c);
    }

    @Test
    void customConstructor() {
        LibreOfficeConverter c = new LibreOfficeConverter("soffice", 60);
        assertNotNull(c);
    }

    @Test
    void customConstructorWithCustomCommand() {
        LibreOfficeConverter c = new LibreOfficeConverter("/usr/local/bin/soffice", 30);
        assertNotNull(c);
    }

    @Test
    void convertToPdfMissingInputThrows() {
        LibreOfficeConverter c = new LibreOfficeConverter();
        var error = assertThrows(OfficeKitError.class,
                () -> c.convertToPdf("/nonexistent-file-xyz.docx", "/tmp"));
        assertEquals(OfficeKitError.INPUT_NOT_FOUND, error.errorCode());
    }

    @Test
    void convertToPdfCannotCreateOutputDirThrows() throws Exception {
        Path input = Files.createTempFile("test-input", ".docx");
        try {
            LibreOfficeConverter c = new LibreOfficeConverter();
            var error = assertThrows(OfficeKitError.class,
                    () -> c.convertToPdf(input.toString(), "/dev/null/cannot-create-dir/output"));
            assertEquals(OfficeKitError.CONVERSION_FAILED, error.errorCode());
        } finally {
            Files.deleteIfExists(input);
        }
    }

    @Test
    void convertToPdfExistingInputButSofficeMissingThrows() throws Exception {
        Path input = Files.createTempFile("test-input", ".docx");
        Path outDir = Files.createTempDirectory("lo-out");
        try {
            LibreOfficeConverter c = new LibreOfficeConverter("nonexistent-soffice-cmd-xyz", 5);
            var error = assertThrows(OfficeKitError.class,
                    () -> c.convertToPdf(input.toString(), outDir.toString()));
            assertEquals(OfficeKitError.UNAVAILABLE, error.errorCode());
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(outDir);
        }
    }

    @Test
    void convertToPdfSofficeNonZeroExitThrows() throws Exception {
        Path input = Files.createTempFile("test-input", ".docx");
        Path outDir = Files.createTempDirectory("lo-out");
        try {
            LibreOfficeConverter c = new LibreOfficeConverter("false", 5);
            var error = assertThrows(OfficeKitError.class,
                    () -> c.convertToPdf(input.toString(), outDir.toString()));
            assertEquals(OfficeKitError.CONVERSION_FAILED, error.errorCode());
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(outDir);
        }
    }

    @Test
    void convertToPdfSofficeTimeoutThrows() throws Exception {
        Path input = Files.createTempFile("test-input", ".docx");
        Path outDir = Files.createTempDirectory("lo-out");
        try {
            LibreOfficeConverter c = new LibreOfficeConverter("sleep", 1);
            var error = assertThrows(OfficeKitError.class,
                    () -> c.convertToPdf(input.toString(), outDir.toString()));
            assertEquals(OfficeKitError.CONVERSION_FAILED, error.errorCode());
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(outDir);
        }
    }
}
