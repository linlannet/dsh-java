package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.DeviceDTO;
import net.linlan.dsh.api.dto.DevicePairRequestDTO;
import net.linlan.dsh.api.dto.DevicePairResponseDTO;
import net.linlan.dsh.api.dto.DevicePairingCodeDTO;
import net.linlan.dsh.api.dto.MobileAccessDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;

/**
 * 移动设备配对与凭据管理用例接口。
 */
public interface IDeviceApi {

    /**
     * 读取移动设备访问开关。
     *
     * @return 是否开启
     */
    Response<Boolean> getMobileAccess();

    /**
     * 设置移动设备访问开关（默认关闭）。
     *
     * @param request 开关请求
     * @return 设置后的状态
     */
    Response<Boolean> setMobileAccess(MobileAccessDTO request);

    /**
     * 桌面端生成一次性配对码（10 分钟有效）。
     *
     * @return 配对码
     */
    Response<DevicePairingCodeDTO> createPairingCode();

    /**
     * 移动端提交配对码换取设备凭据。
     *
     * @param request 配对请求
     * @return 设备与凭据
     */
    Response<DevicePairResponseDTO> pair(DevicePairRequestDTO request);

    /**
     * 查询已配对设备。
     *
     * @return 设备列表
     */
    Response<List<DeviceDTO>> listDevices();

    /**
     * 吊销单台设备。
     *
     * @param deviceId 设备 ID
     * @return 空响应
     */
    Response<Void> revoke(String deviceId);

    /**
     * 轮换设备凭据，旧 token 立即失效。
     *
     * @param deviceId 设备 ID
     * @return 新凭据
     */
    Response<DevicePairResponseDTO> rotate(String deviceId);

    /**
     * 校验设备级 Bearer Token 是否有效（吊销后立即失效）。
     *
     * @param token 设备 token
     * @return true 表示有效
     */
    Response<Boolean> validateDeviceToken(String token);
}