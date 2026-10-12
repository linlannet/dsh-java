package net.linlan.dsh.infrastructure.adapter.credentials;

import net.linlan.dsh.domain.credentials.anonymous.adapter.port.IAnonymousIdStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JdbcAnonymousIdStoreTest {

    private static final String DB_URL = "jdbc:h2:mem:anon-id-test;MODE=MySQL;DB_CLOSE_DELAY=-1";
    private DataSource dataSource;
    private JdbcAnonymousIdStore store;

    @BeforeEach
    void setUp() {
        dataSource = new SimpleDataSource(DB_URL);
        store = new JdbcAnonymousIdStore(dataSource);
    }

    @Test
    void implementsAnonymousIdStorePort() {
        assertTrue(store instanceof IAnonymousIdStorePort);
    }

    @Test
    void findByIdReturnsEmptyForMissing() {
        assertTrue(store.findById("missing").isEmpty());
    }

    @Test
    void findByIdNullReturnsEmpty() {
        assertTrue(store.findById(null).isEmpty());
    }

    @Test
    void saveAndFindById() {
        store.save("fp-save-find", "anon-1");
        Optional<String> found = store.findById("fp-save-find");
        assertTrue(found.isPresent());
        assertEquals("anon-1", found.get());
    }

    @Test
    void saveMultipleEntries() {
        store.save("fp-multi-1", "anon-1");
        store.save("fp-multi-2", "anon-2");
        assertEquals("anon-1", store.findById("fp-multi-1").get());
        assertEquals("anon-2", store.findById("fp-multi-2").get());
    }

    @Test
    void ensureTableIsIdempotent() {
        JdbcAnonymousIdStore second = new JdbcAnonymousIdStore(dataSource);
        assertNotNull(second);
        second.save("fp-idem", "anon-3");
        assertEquals("anon-3", store.findById("fp-idem").get());
    }

    @Test
    void saveAllowsLookupByFingerprint() {
        store.save("fp-lookup", "user-anonymous-id");
        Optional<String> result = store.findById("fp-lookup");
        assertTrue(result.isPresent());
        assertEquals("user-anonymous-id", result.get());
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
