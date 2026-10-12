package net.linlan.dsh.infrastructure.adapter.sandbox.windows;

import org.junit.jupiter.api.Test;

import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;

import static org.junit.jupiter.api.Assertions.*;

class AclDiagnosticTest {

    private final AclDiagnostic diagnostic = new AclDiagnostic();

    @Test
    void nullErrorReturnsNoError() {
        AclDiagnostic.AclDiagnosticResult result = diagnostic.diagnose("/tmp/test", null);
        assertEquals("/tmp/test", result.rejectedPath());
        assertEquals("no error", result.reason());
        assertEquals("no action needed", result.suggestion());
    }

    @Test
    void accessDeniedExceptionRecognized() {
        AccessDeniedException ex = new AccessDeniedException("/secret/file");
        AclDiagnostic.AclDiagnosticResult result = diagnostic.diagnose("/secret/file", ex);
        assertEquals("access denied by ACL", result.reason());
        assertTrue(result.suggestion().contains("grant"));
        assertTrue(result.suggestion().contains("/secret/file"));
    }

    @Test
    void noSuchFileExceptionRecognized() {
        NoSuchFileException ex = new NoSuchFileException("/missing/path");
        AclDiagnostic.AclDiagnosticResult result = diagnostic.diagnose("/missing/path", ex);
        assertEquals("path does not exist", result.reason());
        assertTrue(result.suggestion().contains("create"));
    }

    @Test
    void securityExceptionRecognized() {
        SecurityException ex = new SecurityException("denied");
        AclDiagnostic.AclDiagnosticResult result = diagnostic.diagnose("/tmp/x", ex);
        assertEquals("security manager denied access", result.reason());
        assertTrue(result.suggestion().contains("SecurityManager"));
    }

    @Test
    void accessDeniedByMessageRecognized() {
        RuntimeException ex = new RuntimeException("Access is denied");
        AclDiagnostic.AclDiagnosticResult result = diagnostic.diagnose("/test", ex);
        assertEquals("access denied by ACL", result.reason());
    }

    @Test
    void permissionDeniedByMessageRecognized() {
        RuntimeException ex = new RuntimeException("Permission denied");
        AclDiagnostic.AclDiagnosticResult result = diagnostic.diagnose("/test", ex);
        assertEquals("access denied by ACL", result.reason());
    }

    @Test
    void readOnlyByMessageRecognized() {
        RuntimeException ex = new RuntimeException("Read-only file system");
        AclDiagnostic.AclDiagnosticResult result = diagnostic.diagnose("/ro", ex);
        assertEquals("path is read-only", result.reason());
        assertTrue(result.suggestion().contains("remount"));
    }

    @Test
    void diskFullByMessageRecognized() {
        RuntimeException ex = new RuntimeException("disk full");
        AclDiagnostic.AclDiagnosticResult result = diagnostic.diagnose("/full", ex);
        assertEquals("disk space insufficient", result.reason());
        assertTrue(result.suggestion().contains("free disk space"));
    }

    @Test
    void notFoundByMessageRecognized() {
        RuntimeException ex = new RuntimeException("no such file or directory");
        AclDiagnostic.AclDiagnosticResult result = diagnostic.diagnose("/nope", ex);
        assertEquals("path does not exist", result.reason());
    }

    @Test
    void unknownErrorFallback() {
        RuntimeException ex = new RuntimeException("something weird");
        AclDiagnostic.AclDiagnosticResult result = diagnostic.diagnose("/x", ex);
        assertTrue(result.reason().startsWith("unknown error"));
        assertTrue(result.suggestion().contains("inspect"));
    }

    @Test
    void nullPathNormalizedToUnknown() {
        AclDiagnostic.AclDiagnosticResult result =
                diagnostic.diagnose(null, new RuntimeException("err"));
        assertEquals("<unknown>", result.rejectedPath());
    }

    @Test
    void blankPathNormalizedToUnknown() {
        AclDiagnostic.AclDiagnosticResult result =
                diagnostic.diagnose("  ", new RuntimeException("err"));
        assertEquals("<unknown>", result.rejectedPath());
    }

    @Test
    void pathIsNormalizedToAbsolutePath() {
        AclDiagnostic.AclDiagnosticResult result =
                diagnostic.diagnose("relative/path", null);
        assertTrue(result.rejectedPath().startsWith("/"));
    }
}
