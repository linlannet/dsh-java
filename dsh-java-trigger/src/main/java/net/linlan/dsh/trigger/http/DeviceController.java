package net.linlan.dsh.trigger.http;

import net.linlan.dsh.api.IDeviceApi;
import net.linlan.dsh.api.dto.DeviceDTO;
import net.linlan.dsh.api.dto.DevicePairRequestDTO;
import net.linlan.dsh.api.dto.DevicePairResponseDTO;
import net.linlan.dsh.api.dto.DevicePairingCodeDTO;
import net.linlan.dsh.api.dto.MobileAccessDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 移动设备配对与凭据管理 REST 触发层。
 */
@RestController
@RequestMapping(value = {"/api/devices"})
public class DeviceController {

    private final IDeviceApi deviceApi;

    /**
     * 构造控制器。
     *
     * @param deviceApi 设备用例接口
     */
    public DeviceController(IDeviceApi deviceApi) {
        this.deviceApi = deviceApi;
    }

    /**
     * 查询移动设备访问开关。
     *
     * @return 是否开启
     */
    @GetMapping(value = {"/mobile-access"})
    public Response<Boolean> mobileAccess() {
        return this.deviceApi.getMobileAccess();
    }

    /**
     * 设置移动设备访问开关。
     *
     * @param request 开关请求
     * @return 设置后的状态
     */
    @PutMapping(value = {"/mobile-access"})
    public Response<Boolean> setMobileAccess(@RequestBody(required = false) MobileAccessDTO request) {
        return this.deviceApi.setMobileAccess(request);
    }

    /**
     * 桌面端生成一次性配对码。
     *
     * @return 配对码
     */
    @PostMapping(value = {"/pairing-codes"})
    public Response<DevicePairingCodeDTO> createPairingCode() {
        return this.deviceApi.createPairingCode();
    }

    /**
     * 移动端提交配对码换取设备凭据。
     *
     * @param request 配对请求
     * @return 设备与凭据
     */
    @PostMapping(value = {"/pair"})
    public Response<DevicePairResponseDTO> pair(@RequestBody(required = false) DevicePairRequestDTO request) {
        return this.deviceApi.pair(request == null ? new DevicePairRequestDTO(null, null, null) : request);
    }

    /**
     * 查询已配对设备。
     *
     * @return 设备列表
     */
    @GetMapping
    public Response<List<DeviceDTO>> list() {
        return this.deviceApi.listDevices();
    }

    /**
     * 吊销单台设备。
     *
     * @param deviceId 设备 ID
     * @return 空响应
     */
    @DeleteMapping(value = {"/{deviceId}"})
    public Response<Void> revoke(@PathVariable(value = "deviceId") String deviceId) {
        return this.deviceApi.revoke(deviceId);
    }

    /**
     * 轮换设备凭据。
     *
     * @param deviceId 设备 ID
     * @return 新凭据
     */
    @PostMapping(value = {"/{deviceId}/rotate"})
    public Response<DevicePairResponseDTO> rotate(@PathVariable(value = "deviceId") String deviceId) {
        return this.deviceApi.rotate(deviceId);
    }
}