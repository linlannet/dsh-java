package net.linlan.dsh.infrastructure.adapter.storage;

import net.linlan.dsh.domain.sdk.spill.adapter.port.ISpillStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class JdbcSpillStoreTest {

    private static final String DB_URL = "jdbc:h2:mem:spill-store-test;DB_CLOSE_DELAY=-1";
    private DataSource dataSource;
    private JdbcSpillStore store;

    @BeforeEach
    void setUp() {
        dataSource = new SimpleDataSource(DB_URL);
        store = new JdbcSpillStore(dataSource);
    }

    @Test
    void implementsSpillStorePort() {
        assertTrue(store instanceof ISpillStorePort);
    }

    @Test
    void loadReturnsNullForMissing() {
        assertNull(store.load("missing"));
    }

    @Test
    void existsReturnsFalseForMissing() {
        assertFalse(store.exists("missing"));
    }

    @Test
    void saveLoadAndExists() {
        store.save("ref1", "content-1");
        assertTrue(store.exists("ref1"));
        assertEquals("content-1", store.load("ref1"));
    }

    @Test
    void saveNullContent() {
        store.save("ref2", null);
        assertTrue(store.exists("ref2"));
        assertNull(store.load("ref2"));
    }

    @Test
    void saveEmptyContent() {
        store.save("ref3", "");
        assertTrue(store.exists("ref3"));
        assertEquals("", store.load("ref3"));
    }

    @Test
    void saveLargeContent() {
        String large = "x".repeat(10000);
        store.save("big", large);
        assertEquals(large, store.load("big"));
    }

    @Test
    void ensureTableIsIdempotent() {
        JdbcSpillStore second = new JdbcSpillStore(dataSource);
        assertNotNull(second);
        second.save("ref4", "content-4");
        assertEquals("content-4", store.load("ref4"));
    }

    @Test
    void existsReturnsTrueAfterSave() {
        assertFalse(store.exists("ref5"));
        store.save("ref5", "data");
        assertTrue(store.exists("ref5"));
    }

    @Test
    void loadReturnsContentWithSpecialChars() {
        String special = "line1\nline2\ttab\"quote";
        store.save("special", special);
        assertEquals(special, store.load("special"));
    }

    static class SimpleDataSource implements DataSource {
        private final String url;

        SimpleDataSource(String url) {
            this.url = url;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, "sa", "");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public java.io.PrintWriter getLogWriter() { return null; }

        @Override
        public void setLogWriter(java.io.PrintWriter out) { }

        @Override
        public void setLoginTimeout(int seconds) { }

        @Override
        public int getLoginTimeout() { return 0; }

        @Override
        public java.util.logging.Logger getParentLogger() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
