package net.linlan.dsh.infrastructure.device;

import net.linlan.dsh.domain.device.adapter.repository.IDeviceRepository;
import net.linlan.dsh.domain.device.model.DeviceCredential;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * 设备凭据内存仓储（最小闭环实现，后续可替换为数据库仓储）。
 */
@Repository
public class InMemoryDeviceRepository implements IDeviceRepository {

    private final Map<String, DeviceCredential> store = new ConcurrentHashMap<>();

    @Override
    public Optional<DeviceCredential> findByDeviceId(String deviceId) {
        return Optional.ofNullable(this.store.get(deviceId));
    }

    @Override
    public List<DeviceCredential> findAll() {
        return new ArrayList<>(this.store.values());
    }

    @Override
    public void save(DeviceCredential credential) {
        this.store.put(credential.deviceId(), credential);
    }

    @Override
    public void delete(String deviceId) {
        this.store.remove(deviceId);
    }
}