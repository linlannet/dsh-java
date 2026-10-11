package net.linlan.dsh.infrastructure.adapter.storage;

import net.linlan.dsh.domain.storage.adapter.port.IStorageBackendPort;
import net.linlan.dsh.domain.storage.model.valobj.KvSnapshot;
import net.linlan.dsh.domain.storage.model.valobj.KvUnitDescriptor;
import net.linlan.dsh.domain.storage.model.valobj.StorageErrorCode;
import net.linlan.dsh.domain.storage.model.valobj.StorageException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON storage backend: one human-readable file per unit under a configured
 * root, published by atomic whole-file rewrite.
 *
 * <p>File layout: {@code <root>/<unit>.json}, each containing a JSON object of
 * table → key → value.
 *
 *
 */
public class JsonStorageBackendPort implements IStorageBackendPort {

    private static final TypeReference<Map<String, Map<String, String>>> MAP_TYPE =
            new TypeReference<>() {};

    private final Path root;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Object> unitLocks = new ConcurrentHashMap<>();

    /**
     * 处理JSON Storage Backend Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param root 根路径。
     * @param objectMapper object Mapper参数。
     * @return 方法执行结果。
     */
    public JsonStorageBackendPort(Path root, ObjectMapper objectMapper) {
        this.root = root;
        this.objectMapper = objectMapper;
        if (!Files.exists(root)) {
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                throw new StorageException(StorageErrorCode.MALFORMED_MEDIUM,
                        "cannot create storage root: " + root, e);
            }
        }
    }
    /**
     * 加载。
     */

    @Override
    public KvSnapshot load(KvUnitDescriptor descriptor) {
        Path file = unitFile(descriptor);
        if (!Files.exists(file)) return new KvSnapshot(Map.of());
        try {
            synchronized (lockFor(descriptor)) {
                Map<String, Map<String, String>> data = objectMapper.readValue(file.toFile(), MAP_TYPE);
                return new KvSnapshot(data);
            }
        } catch (IOException e) {
            throw new StorageException(StorageErrorCode.MALFORMED_MEDIUM,
                    "cannot read unit file: " + file, e);
        }
    }
    /**
     * put。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param descriptor descriptor参数。
     * @param table table参数。
     * @param key key参数。
     * @param value 参数值。
     */


    @Override
    public void put(KvUnitDescriptor descriptor, String table, String key, String value) {
        synchronized (lockFor(descriptor)) {
            Map<String, Map<String, String>> data = readRaw(descriptor);
            data.computeIfAbsent(table, t -> new LinkedHashMap<>()).put(key, value);
            writeRaw(descriptor, data);
        }
    }
    /**
     * 移除目标对象。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param descriptor descriptor参数。
     * @param table table参数。
     * @param key key参数。
     */


    @Override
    public void remove(KvUnitDescriptor descriptor, String table, String key) {
        synchronized (lockFor(descriptor)) {
            Map<String, Map<String, String>> data = readRaw(descriptor);
            Map<String, String> tableData = data.get(table);
            if (tableData != null) {
                tableData.remove(key);
                if (tableData.isEmpty()) data.remove(table);
                writeRaw(descriptor, data);
            }
        }
    }
    /**
     * put All。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param descriptor descriptor参数。
     * @param Map<String Map String参数。
     * @param Map<String Map String参数。
     * @param puts puts参数。
     */


    @Override
    public void putAll(KvUnitDescriptor descriptor, Map<String, Map<String, String>> puts) {
        synchronized (lockFor(descriptor)) {
            Map<String, Map<String, String>> data = readRaw(descriptor);
            for (var entry : puts.entrySet()) {
                data.computeIfAbsent(entry.getKey(), t -> new LinkedHashMap<>())
                        .putAll(entry.getValue());
            }
            writeRaw(descriptor, data);
        }
    }

    /**
     * 读取 Raw。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param descriptor descriptor参数。
     * @return 方法执行结果。
     */
    private Map<String, Map<String, String>> readRaw(KvUnitDescriptor descriptor) {
        Path file = unitFile(descriptor);
        if (!Files.exists(file)) return new LinkedHashMap<>();
        try {
            Map<String, Map<String, String>> data = objectMapper.readValue(file.toFile(), MAP_TYPE);
            return data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
        } catch (IOException e) {
            throw new StorageException(StorageErrorCode.MALFORMED_MEDIUM,
                    "cannot read unit file: " + file, e);
        }
    }

    /**
     * 写入 Raw。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param descriptor descriptor参数。
     * @param Map<String 映射 String参数。
     * @param Map<String 映射 String参数。
     * @param data 数据内容。
     */
    private void writeRaw(KvUnitDescriptor descriptor, Map<String, Map<String, String>> data) {
        Path file = unitFile(descriptor);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
        } catch (IOException e) {
            throw new StorageException(StorageErrorCode.MALFORMED_MEDIUM,
                    "cannot write unit file: " + file, e);
        }
    }

    /**
     * 处理unit 文件。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param descriptor descriptor参数。
     * @return 方法执行结果。
     */
    private Path unitFile(KvUnitDescriptor descriptor) {
        return root.resolve(descriptor.name() + ".json");
    }

    /**
     * 处理lock For。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param descriptor descriptor参数。
     * @return 方法执行结果。
     */
    private Object lockFor(KvUnitDescriptor descriptor) {
        return unitLocks.computeIfAbsent(descriptor.name(), n -> new Object());
    }
}
