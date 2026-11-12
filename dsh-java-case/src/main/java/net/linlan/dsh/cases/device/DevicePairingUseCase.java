package net.linlan.dsh.cases.device;

import net.linlan.dsh.api.IDeviceApi;
import net.linlan.dsh.api.dto.DeviceDTO;
import net.linlan.dsh.api.dto.DevicePairRequestDTO;
import net.linlan.dsh.api.dto.DevicePairResponseDTO;
import net.linlan.dsh.api.dto.DevicePairingCodeDTO;
import net.linlan.dsh.api.dto.MobileAccessDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.domain.device.adapter.repository.IDeviceRepository;
import net.linlan.dsh.domain.device.model.aggregate.DeviceCredentialAggregate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

/**
 * 设备配对与凭据用例。
 *
 * <p>配对码一次性且 10 分钟过期；设备级 token 仅签发时返回一次，服务端只保存摘要。
 * 移动设备访问默认关闭，需在桌面端显式开启。
 */
@Service
public class DevicePairingUseCase implements IDeviceApi {

    private static final long PAIRING_TTL_MS = 10 * 60 * 1000L;
    private static final int MAX_ATTEMPTS_PER_WINDOW = 10;
    private static final long ATTEMPT_WINDOW_MS = 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IDeviceRepository repository;
    private final AtomicBoolean mobileAccess = new AtomicBoolean(false);
    private final Map<String, Long> pairingCodes = new ConcurrentHashMap<>();
    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    /**
     * 构造用例。
     *
     * @param repository 设备仓储
     */
    public DevicePairingUseCase(IDeviceRepository repository) {
        this.repository = repository;
    }

    @Override
    public Response<Boolean> getMobileAccess() {
        return Response.success(this.mobileAccess.get());
    }

    @Override
    public Response<Boolean> setMobileAccess(MobileAccessDTO request) {
        this.mobileAccess.set(request != null && request.enabled());
        if (!this.mobileAccess.get()) {
            this.pairingCodes.clear();
        }
        return Response.success(this.mobileAccess.get());
    }

    @Override
    public Response<DevicePairingCodeDTO> createPairingCode() {
        if (!this.mobileAccess.get()) {
            return Response.fail("40300", "移动设备访问未开启");
        }
        this.purgeExpiredCodes();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        long expiresAt = System.currentTimeMillis() + PAIRING_TTL_MS;
        this.pairingCodes.put(code, expiresAt);
        return Response.success(new DevicePairingCodeDTO(
                code, Instant.ofEpochMilli(expiresAt).toString(), PAIRING_TTL_MS / 1000));
    }

    @Override
    public Response<DevicePairResponseDTO> pair(DevicePairRequestDTO request) {
        if (!this.mobileAccess.get()) {
            return Response.fail("40300", "移动设备访问未开启");
        }
        if (request == null || isBlank(request.pairingCode())) {
            return Response.invalidArgument("配对码不能为空");
        }
        if (isBlank(request.deviceName()) || request.deviceName().length() > 40) {
            return Response.invalidArgument("设备名称必须为 1–40 字符");
        }
        if (isBlank(request.fingerprint())) {
            return Response.invalidArgument("设备指纹不能为空");
        }
        if (!allowAttempt(request.fingerprint())) {
            return Response.fail("42900", "配对尝试过于频繁，请稍后再试");
        }

        Long expiresAt = this.pairingCodes.remove(request.pairingCode());
        if (expiresAt == null) {
            return Response.fail("40001", "配对码无效或已使用");
        }
        if (System.currentTimeMillis() > expiresAt) {
            return Response.fail("40002", "配对码已过期");
        }

        if (this.repository.findAll().stream()
                .anyMatch(item -> item.fingerprint().equals(fingerprintDigest(request.fingerprint())))) {
            return Response.fail("40003", "该设备已配对，请先吊销后再配对");
        }

        String deviceId = "dev-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String token = issueToken(deviceId);
        String now = Instant.now().toString();
        this.repository.save(new DeviceCredentialAggregate(
                deviceId,
                request.deviceName().trim(),
                fingerprintDigest(request.fingerprint()),
                hash(token),
                now,
                now));
        return Response.success(new DevicePairResponseDTO(deviceId, request.deviceName().trim(), token, now));
    }

    @Override
    public Response<List<DeviceDTO>> listDevices() {
        List<DeviceDTO> devices = this.repository.findAll().stream()
                .map(item -> new DeviceDTO(
                        item.deviceId(), item.deviceName(), item.fingerprint(), item.createdAt(), item.lastSeenAt()))
                .toList();
        return Response.success(devices);
    }

    @Override
    public Response<Void> revoke(String deviceId) {
        if (isBlank(deviceId)) {
            return Response.invalidArgument("设备 ID 不能为空");
        }
        this.repository.delete(deviceId);
        return Response.success(null);
    }

    @Override
    public Response<DevicePairResponseDTO> rotate(String deviceId) {
        if (isBlank(deviceId)) {
            return Response.invalidArgument("设备 ID 不能为空");
        }
        return this.repository.findByDeviceId(deviceId)
                .map(existing -> {
                    String token = issueToken(deviceId);
                    String now = Instant.now().toString();
                    this.repository.save(new DeviceCredentialAggregate(
                            existing.deviceId(), existing.deviceName(), existing.fingerprint(),
                            hash(token), existing.createdAt(), now));
                    return Response.success(new DevicePairResponseDTO(deviceId, existing.deviceName(), token, now));
                })
                .orElseGet(() -> Response.fail("40004", "设备不存在"));
    }

    @Override
    public Response<Boolean> validateDeviceToken(String token) {
        if (isBlank(token)) {
            return Response.success(Boolean.FALSE);
        }
        String digest = hash(token);
        boolean valid = this.repository.findAll().stream()
                .anyMatch(item -> item.tokenHash().equals(digest));
        return Response.success(valid);
    }

    private boolean allowAttempt(String fingerprint) {
        long now = System.currentTimeMillis();
        String key = fingerprintDigest(fingerprint);
        AttemptWindow window = this.attempts.get(key);
        if (window == null || now - window.startedAt() > ATTEMPT_WINDOW_MS) {
            this.attempts.put(key, new AttemptWindow(now, 1));
            return true;
        }
        if (window.count() >= MAX_ATTEMPTS_PER_WINDOW) {
            return false;
        }
        this.attempts.put(key, new AttemptWindow(window.startedAt(), window.count() + 1));
        return true;
    }

    private void purgeExpiredCodes() {
        long now = System.currentTimeMillis();
        this.pairingCodes.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private static String issueToken(String deviceId) {
        return "devt_" + deviceId + "_" + UUID.randomUUID() + UUID.randomUUID();
    }

    private static String fingerprintDigest(String fingerprint) {
        return hash(fingerprint).substring(0, 32);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record AttemptWindow(long startedAt, int count) {
    }
}