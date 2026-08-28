package org.dromara.payment.service;

import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.domain.PmSensitiveOperationLog;
import org.dromara.payment.mapper.SensitiveOperationLogMapper;
import org.dromara.payment.security.StepUpVerificationMethod;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class SensitiveOperationLogServiceTest {

    @Test
    void recordsMfaOperatorAndJsonSnapshots() throws Exception {
        SensitiveOperationLogMapper mapper =
            mock(SensitiveOperationLogMapper.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any()))
            .thenReturn("{\"fixture\":true}");
        SensitiveOperationLogService service =
            new SensitiveOperationLogService(
                mapper,
                objectMapper,
                mock(MerchantDisplayService.class));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            service.record(
                1L,
                "FORCE_MATCH",
                "PAYMENT_ORDER",
                2L,
                "fixture",
                Map.of("eventId", 3L),
                "{\"before\":true}",
                Map.of("orderId", 2L),
                StepUpVerificationMethod.MFA,
                "FORCE_MATCH:2:3");
        }

        ArgumentCaptor<PmSensitiveOperationLog> captor =
            ArgumentCaptor.forClass(PmSensitiveOperationLog.class);
        verify(mapper).insert(captor.capture());
        PmSensitiveOperationLog log = captor.getValue();
        assertEquals("FORCE_MATCH", log.getOperationType());
        assertEquals("MFA", log.getVerificationMethod());
        assertEquals(9L, log.getOperatedBy());
        assertEquals("{\"before\":true}", log.getBeforeSnapshot());
        assertEquals("FORCE_MATCH:2:3", log.getIdempotencyKey());
    }

    @Test
    void recordsSessionVerificationMethod() throws Exception {
        SensitiveOperationLogMapper mapper =
            mock(SensitiveOperationLogMapper.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any()))
            .thenReturn("{\"fixture\":true}");
        SensitiveOperationLogService service =
            new SensitiveOperationLogService(
                mapper,
                objectMapper,
                mock(MerchantDisplayService.class));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);
            service.record(
                1L,
                "FORCE_MATCH",
                "PAYMENT_ORDER",
                2L,
                "fixture",
                Map.of("eventId", 3L),
                "{\"before\":true}",
                Map.of("orderId", 2L),
                StepUpVerificationMethod.SESSION,
                "FORCE_MATCH:2:3");
        }

        ArgumentCaptor<PmSensitiveOperationLog> captor =
            ArgumentCaptor.forClass(PmSensitiveOperationLog.class);
        verify(mapper).insert(captor.capture());
        assertEquals("SESSION", captor.getValue().getVerificationMethod());
    }
}
