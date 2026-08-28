package org.dromara.payment.integration.epay.controller;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.integration.epay.application.EpayOperationsService;
import org.dromara.payment.integration.epay.application.PaymentIntegrationService;
import org.dromara.payment.integration.epay.domain.dto.PaymentIntegrationRouteSaveRequest;
import org.dromara.payment.integration.epay.domain.dto.PaymentIntegrationSaveRequest;
import org.dromara.payment.integration.epay.domain.dto.ProtocolCallbackReplayRequest;
import org.dromara.payment.service.AccountMfaService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("dev")
class PaymentIntegrationControllerTest {

    @Test
    void createVerifiesOperationBoundMfaBeforeWriting() {
        PaymentIntegrationService service = mock(PaymentIntegrationService.class);
        AccountMfaService mfaService = mock(AccountMfaService.class);
        doThrow(new ServiceException("敏感操作需要 MFA 二次验证"))
            .when(mfaService)
            .requireStepUp(null, "EPAY_INTEGRATION_WRITE");
        PaymentIntegrationController controller =
            new PaymentIntegrationController(service, mfaService);

        assertThrows(
            ServiceException.class,
            () -> controller.create(new PaymentIntegrationSaveRequest(), null));

        verify(service, never()).create(any());
    }

    @Test
    void savingEmptyRoutesStillRequiresMfaAndReachesService() {
        PaymentIntegrationService service = mock(PaymentIntegrationService.class);
        AccountMfaService mfaService = mock(AccountMfaService.class);
        PaymentIntegrationController controller =
            new PaymentIntegrationController(service, mfaService);
        PaymentIntegrationRouteSaveRequest request =
            new PaymentIntegrationRouteSaveRequest();
        request.setRoutes(List.of());

        controller.saveRoutes(7L, request, "step-up-token");

        verify(mfaService).requireStepUp(
            "step-up-token",
            "EPAY_INTEGRATION_WRITE");
        verify(service).saveRoutes(7L, request);
    }

    @Test
    void callbackRetryAndReplayUseTheSameMfaOperation() {
        EpayOperationsService service = mock(EpayOperationsService.class);
        AccountMfaService mfaService = mock(AccountMfaService.class);
        EpayOperationsController controller =
            new EpayOperationsController(service, mfaService);
        ProtocolCallbackReplayRequest request = new ProtocolCallbackReplayRequest();
        request.setReason("人工兼容回归");

        controller.retry(8L, "retry-token");
        controller.replay(9L, request, "replay-token");

        verify(mfaService).requireStepUp(
            "retry-token",
            "EPAY_INTEGRATION_WRITE");
        verify(mfaService).requireStepUp(
            "replay-token",
            "EPAY_INTEGRATION_WRITE");
        verify(service).retry(8L);
        verify(service).replay(9L, "人工兼容回归");
    }
}
