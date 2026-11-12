package net.linlan.dsh.domain.device.adapter.repository;

import net.linlan.dsh.domain.device.model.aggregate.DeviceCredentialAggregate;
import java.util.List;
import java.util.Optional;

/**
 * 设备凭据仓储。
 */
public interface IDeviceRepository {

    /**
     * 按设备 ID 查询。
     *
     * @param deviceId 设备 ID
     * @return 设备凭据
     */
    Optional<DeviceCredentialAggregate> findByDeviceId(String deviceId);

    /**
     * 查询全部设备。
     *
     * @return 设备列表
     */
    List<DeviceCredentialAggregate> findAll();

    /**
     * 保存设备凭据。
     *
     * @param credential 设备凭据
     */
    void save(DeviceCredentialAggregate credential);

    /**
     * 删除设备。
     *
     * @param deviceId 设备 ID
     */
    void delete(String deviceId);
}