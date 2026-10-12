package net.linlan.dsh.infrastructure.adapter.credentials;

import net.linlan.dsh.domain.credentials.authorization.adapter.port.ICredentialDialogPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class HttpCredentialDialogTest {

    private InputStream originalIn;

    @BeforeEach
    void setUp() {
        originalIn = System.in;
    }

    @AfterEach
    void tearDown() {
        System.setIn(originalIn);
    }

    @Test
    void implementsCredentialDialogPort() {
        HttpCredentialDialog dialog = new HttpCredentialDialog();
        assertTrue(dialog instanceof ICredentialDialogPort);
    }

    @Test
    void promptCredentialReturnsEnteredValue() {
        System.setIn(new ByteArrayInputStream("my-token\n".getBytes()));
        HttpCredentialDialog dialog = new HttpCredentialDialog();
        var result = dialog.promptCredential("svc", "scope");
        assertTrue(result.isPresent());
        assertEquals("my-token", result.get());
    }

    @Test
    void promptCredentialReturnsEmptyForBlankLine() {
        System.setIn(new ByteArrayInputStream("\n".getBytes()));
        HttpCredentialDialog dialog = new HttpCredentialDialog();
        var result = dialog.promptCredential("svc", "scope");
        assertTrue(result.isEmpty());
    }

    @Test
    void promptCredentialReturnsEmptyWhenNoInput() {
        System.setIn(new ByteArrayInputStream(new byte[0]));
        HttpCredentialDialog dialog = new HttpCredentialDialog();
        var result = dialog.promptCredential("svc", "scope");
        assertTrue(result.isEmpty());
    }

    @Test
    void promptCredentialReturnsMultilineFirstLine() {
        System.setIn(new ByteArrayInputStream("first\nsecond\n".getBytes()));
        HttpCredentialDialog dialog = new HttpCredentialDialog();
        var result = dialog.promptCredential("svc", "scope");
        assertTrue(result.isPresent());
        assertEquals("first", result.get());
    }

    @Test
    void promptCredentialReturnsValueWithSpaces() {
        System.setIn(new ByteArrayInputStream("token with spaces\n".getBytes()));
        HttpCredentialDialog dialog = new HttpCredentialDialog();
        var result = dialog.promptCredential("svc", "scope");
        assertTrue(result.isPresent());
        assertEquals("token with spaces", result.get());
    }
}
