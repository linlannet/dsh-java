package net.linlan.dsh.domain.storage.domain;

import net.linlan.dsh.domain.storage.adapter.port.IStorageBackendPort;
import net.linlan.dsh.domain.storage.model.valobj.KvSnapshot;
import net.linlan.dsh.domain.storage.model.valobj.KvUnitDescriptor;
import net.linlan.dsh.domain.storage.model.valobj.StorageErrorCode;
import net.linlan.dsh.domain.storage.model.valobj.StorageException;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class StorageDomain {

    private static final String DEFAULT_TABLE = "kv";
    private static final String DEFAULT_UNIT = "storage-domain";

    private final IStorageBackendPort backend;
    private final java.util.function.Consumer<StorageEvent> eventSink;

    public StorageDomain(IStorageBackendPort backend) {
        this(backend, null);
    }

    public StorageDomain(IStorageBackendPort backend, java.util.function.Consumer<StorageEvent> eventSink) {
        if (backend == null) {
            throw new IllegalArgumentException("backend required");
        }
        this.backend = backend;
        this.eventSink = eventSink;
    }

    public void put(String key, String value, String schema) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key required");
        }
        if (value == null) {
            throw new IllegalArgumentException("value required");
        }
        if (schema != null && !schema.isBlank()) {
            validate(value, schema);
        }
        KvUnitDescriptor descriptor = new KvUnitDescriptor(DEFAULT_UNIT, Set.of(DEFAULT_TABLE));
        backend.put(descriptor, DEFAULT_TABLE, key, value);
        emit(new StorageEvent("put", key, value));
    }

    public Optional<String> get(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key required");
        }
        KvUnitDescriptor descriptor = new KvUnitDescriptor(DEFAULT_UNIT, Set.of(DEFAULT_TABLE));
        KvSnapshot snapshot = backend.load(descriptor);
        Map<String, String> table = snapshot.tables().get(DEFAULT_TABLE);
        if (table == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(table.get(key));
    }

    public void validate(String value, String schema) {
        Objects.requireNonNull(value, "value required");
        Objects.requireNonNull(schema, "schema required");
        if (schema.isBlank()) {
            return;
        }
        switch (schema) {
            case "string" -> { }
            case "non-empty" -> {
                if (value.isBlank()) {
                    throw new StorageException(StorageErrorCode.MALFORMED_MEDIUM, "value must be non-empty");
                }
            }
            case "json" -> {
                if (!looksLikeJson(value)) {
                    throw new StorageException(StorageErrorCode.MALFORMED_MEDIUM, "value is not valid json");
                }
            }
            default -> {
                if (!value.matches(schema)) {
                    throw new StorageException(StorageErrorCode.MALFORMED_MEDIUM, "value does not match schema: " + schema);
                }
            }
        }
    }

    private boolean looksLikeJson(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        char first = trimmed.charAt(0);
        char last = trimmed.charAt(trimmed.length() - 1);
        return (first == '{' && last == '}') || (first == '[' && last == ']');
    }

    private void emit(StorageEvent event) {
        if (eventSink != null) {
            eventSink.accept(event);
        }
    }

    public record StorageEvent(String op, String key, String value) {}
}
