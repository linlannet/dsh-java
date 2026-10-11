package net.linlan.dsh.infrastructure.adapter.eventstore;

import net.linlan.dsh.domain.session.event.adapter.repository.SessionPersistenceSeam;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * JsonlSessionPersistenceContractTest 对 {@link JsonlSessionEventStore}
 * 跑 {@link SessionPersistenceContractTest} 全部契约用例。
 */
class JsonlSessionPersistenceContractTest extends SessionPersistenceContractTest {

    @TempDir
    Path tempDir;

    @Override
    protected SessionPersistenceSeam createStore() {
        return new JsonlSessionEventStore(tempDir.toString());
    }
}
