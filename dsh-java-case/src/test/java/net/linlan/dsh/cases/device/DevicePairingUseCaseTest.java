package net.linlan.dsh.cases.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.linlan.dsh.api.dto.DevicePairRequestDTO;
import net.linlan.dsh.api.dto.DevicePairResponseDTO;
import net.linlan.dsh.api.dto.DevicePairingCodeDTO;
import net.linlan.dsh.api.dto.MobileAccessDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.domain.device.adapter.repository.IDeviceRepository;
import net.linlan.dsh.domain.device.model.aggregate.DeviceCredentialAggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DevicePairingUseCaseTest {

    private DevicePairingUseCase useCase;

    @BeforeEach
    void setUp() {
        this.useCase = new DevicePairingUseCase(new MemoryDeviceRepository());
    }

    @Test
    void mobileAccessIsDisabledByDefault() {
        assertEquals(Boolean.FALSE, this.useCase.getMobileAccess().data());
    }

    @Test
    void pairingCodeIsRejectedWhenAccessDisabled() {
        Response<DevicePairingCodeDTO> response = this.useCase.createPairingCode();
        assertEquals("40300", response.code());
    }

    @Test
    void pairingSucceedsWhenAccessEnabled() {
        this.useCase.setMobileAccess(new MobileAccessDTO(true));
        DevicePairingCodeDTO code = this.useCase.createPairingCode().data();
        assertNotNull(code);

        Response<DevicePairResponseDTO> paired = this.useCase.pair(
                new DevicePairRequestDTO(code.pairingCode(), "我的手机", "fingerprint-1"));
        assertEquals(Response.SUCCESS_CODE, paired.code());
        assertNotNull(paired.data().token());
        assertTrue(paired.data().token().startsWith("devt_"));
        assertTrue(Boolean.TRUE.equals(this.useCase.validateDeviceToken(paired.data().token()).data()));
    }

    @Test
    void pairingCodeIsSingleUse() {
        this.useCase.setMobileAccess(new MobileAccessDTO(true));
        DevicePairingCodeDTO code = this.useCase.createPairingCode().data();

        Response<DevicePairResponseDTO> first = this.useCase.pair(
                new DevicePairRequestDTO(code.pairingCode(), "手机A", "fingerprint-a"));
        assertEquals(Response.SUCCESS_CODE, first.code());

        Response<DevicePairResponseDTO> second = this.useCase.pair(
                new DevicePairRequestDTO(code.pairingCode(), "手机B", "fingerprint-b"));
        assertEquals("40001", second.code());
    }

    @Test
    void revokedDeviceTokenBecomesInvalid() {
        this.useCase.setMobileAccess(new MobileAccessDTO(true));
        DevicePairingCodeDTO code = this.useCase.createPairingCode().data();
        DevicePairResponseDTO paired = this.useCase.pair(
                new DevicePairRequestDTO(code.pairingCode(), "手机", "fingerprint-r")).data();

        this.useCase.revoke(paired.deviceId());

        assertFalse(Boolean.TRUE.equals(this.useCase.validateDeviceToken(paired.token()).data()));
        assertEquals(0, this.useCase.listDevices().data().size());
    }

    @Test
    void rotatedTokenReplacesOldOne() {
        this.useCase.setMobileAccess(new MobileAccessDTO(true));
        DevicePairingCodeDTO code = this.useCase.createPairingCode().data();
        DevicePairResponseDTO paired = this.useCase.pair(
                new DevicePairRequestDTO(code.pairingCode(), "手机", "fingerprint-t")).data();

        DevicePairResponseDTO rotated = this.useCase.rotate(paired.deviceId()).data();

        assertNotNull(rotated);
        assertFalse(Boolean.TRUE.equals(this.useCase.validateDeviceToken(paired.token()).data()));
        assertTrue(Boolean.TRUE.equals(this.useCase.validateDeviceToken(rotated.token()).data()));
    }

    @Test
    void disablingAccessDropsPendingCodes() {
        this.useCase.setMobileAccess(new MobileAccessDTO(true));
        DevicePairingCodeDTO code = this.useCase.createPairingCode().data();
        this.useCase.setMobileAccess(new MobileAccessDTO(false));
        this.useCase.setMobileAccess(new MobileAccessDTO(true));

        Response<DevicePairResponseDTO> paired = this.useCase.pair(
                new DevicePairRequestDTO(code.pairingCode(), "手机", "fingerprint-d"));
        assertEquals("40001", paired.code());
    }

    private static final class MemoryDeviceRepository implements IDeviceRepository {

        private final Map<String, DeviceCredentialAggregate> store = new ConcurrentHashMap<>();

        @Override
        public Optional<DeviceCredentialAggregate> findByDeviceId(String deviceId) {
            return Optional.ofNullable(this.store.get(deviceId));
        }

        @Override
        public List<DeviceCredentialAggregate> findAll() {
            return new ArrayList<>(this.store.values());
        }

        @Override
        public void save(DeviceCredentialAggregate credential) {
            this.store.put(credential.deviceId(), credential);
        }

        @Override
        public void delete(String deviceId) {
            this.store.remove(deviceId);
        }
    }
}