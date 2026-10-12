package net.linlan.dsh.infrastructure.adapter.storage;

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
        assertThrows(IllegalArgumentException.class,
                () -> c.convertToPdf("/nonexistent-file-xyz.docx", "/tmp"));
    }

    @Test
    void convertToPdfCannotCreateOutputDirThrows() throws Exception {
        Path input = Files.createTempFile("test-input", ".docx");
        try {
            LibreOfficeConverter c = new LibreOfficeConverter();
            assertThrows(IllegalStateException.class,
                    () -> c.convertToPdf(input.toString(), "/dev/null/cannot-create-dir/output"));
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
            assertThrows(IllegalStateException.class,
                    () -> c.convertToPdf(input.toString(), outDir.toString()));
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
            assertThrows(IllegalStateException.class,
                    () -> c.convertToPdf(input.toString(), outDir.toString()));
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
            assertThrows(IllegalStateException.class,
                    () -> c.convertToPdf(input.toString(), outDir.toString()));
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(outDir);
        }
    }
}
