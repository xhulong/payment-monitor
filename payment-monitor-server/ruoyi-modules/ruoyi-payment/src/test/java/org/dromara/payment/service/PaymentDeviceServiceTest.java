package org.dromara.payment.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.api.DeviceApiException;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmDevice;
import org.dromara.payment.domain.PmDeviceHeartbeat;
import org.dromara.payment.domain.PmPairingCode;
import org.dromara.payment.domain.dto.HeartbeatRequest;
import org.dromara.payment.domain.vo.PairingStatusVo;
import org.dromara.payment.mapper.DeviceCredentialMapper;
import org.dromara.payment.mapper.DeviceHeartbeatMapper;
import org.dromara.payment.mapper.PairingCodeMapper;
import org.dromara.payment.mapper.PaymentDeviceMapper;
import org.dromara.payment.mapper.MerchantMapper;
import org.dromara.payment.security.DeviceSecretCipher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;

@Tag("dev")
class PaymentDeviceServiceTest {

    @Test
    void disabledDeviceRequiresRepair() {
        PaymentDeviceMapper deviceMapper = mock(PaymentDeviceMapper.class);
        PmDevice device = new PmDevice();
        device.setId(1L);
        device.setStatus(PaymentConstants.DEVICE_STATUS_DISABLED);
        when(deviceMapper.selectById(1L)).thenReturn(device);

        PaymentDeviceService service = new PaymentDeviceService(
            deviceMapper,
            mock(DeviceCredentialMapper.class),
            mock(DeviceHeartbeatMapper.class),
            mock(PairingCodeMapper.class),
            mock(MerchantMapper.class),
            mock(DeviceSecretCipher.class),
            new PaymentProperties(),
            mock(DeviceAssignmentService.class),
            mock(AppReleaseService.class),
            mock(MerchantAccessService.class),
            mock(MerchantDisplayService.class));

        DeviceApiException exception = assertThrows(
            DeviceApiException.class,
            () -> service.requireEnabledDevice(1L));

        assertEquals(403, exception.getHttpStatus());
        assertEquals("DEVICE_DISABLED", exception.getErrorCode());
        assertFalse(exception.isRetryable());
        assertTrue(exception.isRePairRequired());
    }

    @Test
    void heartbeatPersistsListenerAndBatteryHealth() {
        PaymentDeviceMapper deviceMapper = mock(PaymentDeviceMapper.class);
        DeviceHeartbeatMapper heartbeatMapper = mock(DeviceHeartbeatMapper.class);
        PmDevice device = new PmDevice();
        device.setId(2L);
        device.setMerchantId(3L);
        device.setStatus(PaymentConstants.DEVICE_STATUS_ENABLED);
        when(deviceMapper.selectById(2L)).thenReturn(device);
        PaymentDeviceService service = new PaymentDeviceService(
            deviceMapper,
            mock(DeviceCredentialMapper.class),
            heartbeatMapper,
            mock(PairingCodeMapper.class),
            mock(MerchantMapper.class),
            mock(DeviceSecretCipher.class),
            new PaymentProperties(),
            mock(DeviceAssignmentService.class),
            mock(AppReleaseService.class),
            mock(MerchantAccessService.class),
            mock(MerchantDisplayService.class));
        HeartbeatRequest request = new HeartbeatRequest();
        request.setMonitoringEnabled(true);
        request.setListenerConnected(false);
        request.setForegroundRunning(true);
        request.setNotificationAccessGranted(true);
        request.setBatteryOptimizationIgnored(false);
        request.setHealthIssue("LISTENER_DISCONNECTED");

        service.heartbeat(2L, request, "203.0.113.50");

        assertTrue(device.getMonitoringEnabled());
        assertFalse(device.getListenerConnected());
        assertEquals("LISTENER_DISCONNECTED", device.getLastHealthIssue());
        ArgumentCaptor<PmDeviceHeartbeat> captor =
            ArgumentCaptor.forClass(PmDeviceHeartbeat.class);
        verify(heartbeatMapper).insert(captor.capture());
        assertFalse(captor.getValue().getListenerConnected());
        assertEquals("LISTENER_DISCONNECTED", captor.getValue().getHealthIssue());
    }

    @Test
    void pairingStatusReportsPendingPairedAndExpiredStates() {
        PaymentDeviceMapper deviceMapper = mock(PaymentDeviceMapper.class);
        PairingCodeMapper pairingCodeMapper = mock(PairingCodeMapper.class);
        PaymentDeviceService service = service(deviceMapper, pairingCodeMapper);
        PmPairingCode code = new PmPairingCode();
        code.setId(11L);
        code.setMerchantId(22L);
        code.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(2));
        when(pairingCodeMapper.selectOne(any())).thenReturn(code);

        MerchantContext.set(22L, false);
        try {
            PairingStatusVo pending = service.queryPairingStatus(11L);
            assertEquals("PENDING", pending.status());

        PmDevice device = new PmDevice();
        device.setId(33L);
        device.setDeviceName("Redmi Note");
        code.setUsedAt(OffsetDateTime.now(ZoneOffset.UTC));
        code.setUsedByDeviceId(33L);
        when(deviceMapper.selectById(33L)).thenReturn(device);

            PairingStatusVo paired = service.queryPairingStatus(11L);
        assertEquals("PAIRED", paired.status());
        assertEquals(33L, paired.deviceId());
        assertEquals("Redmi Note", paired.deviceName());

        code.setUsedAt(null);
        code.setUsedByDeviceId(null);
        code.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
            PairingStatusVo expired = service.queryPairingStatus(11L);
            assertEquals("EXPIRED", expired.status());
        } finally {
            MerchantContext.clear();
        }
    }

    @Test
    void pairingStatusRejectsUnknownOrCrossMerchantSession() {
        PairingCodeMapper pairingCodeMapper = mock(PairingCodeMapper.class);
        when(pairingCodeMapper.selectOne(any())).thenReturn(null);
        PaymentDeviceService service = service(
            mock(PaymentDeviceMapper.class),
            pairingCodeMapper);

        ServiceException exception = assertThrows(
            ServiceException.class,
            () -> service.queryPairingStatus(11L));

        assertEquals("配对记录不存在", exception.getMessage());
    }

    private PaymentDeviceService service(
        PaymentDeviceMapper deviceMapper,
        PairingCodeMapper pairingCodeMapper
    ) {
        return new PaymentDeviceService(
            deviceMapper,
            mock(DeviceCredentialMapper.class),
            mock(DeviceHeartbeatMapper.class),
            pairingCodeMapper,
            mock(MerchantMapper.class),
            mock(DeviceSecretCipher.class),
            new PaymentProperties(),
            mock(DeviceAssignmentService.class),
            mock(AppReleaseService.class),
            mock(MerchantAccessService.class),
            mock(MerchantDisplayService.class));
    }
}
