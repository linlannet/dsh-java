package net.linlan.dsh.infrastructure.adapter.fs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFsServiceTest {

    @TempDir
    Path sessionCwd;

    @Test
    void writesFollowSessionDirectoryButRejectGit() throws Exception {
        var fs = new LocalFsService(sessionCwd.toString(), true);

        fs.write("src/App.java", "class App {}");

        assertTrue(Files.exists(sessionCwd.resolve("src/App.java")));
        assertThrows(SecurityException.class, () -> fs.write(".git/config", "denied"));
        assertThrows(SecurityException.class,
                () -> fs.write("nested/.git/config", "denied", List.of(sessionCwd)));
    }
}
