package org.dromara.payment.controller;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.domain.PmPaymentTransaction;
import org.dromara.payment.domain.dto.ManualOrderMatchRequest;
import org.dromara.payment.domain.dto.TransactionReverseRequest;
import org.dromara.payment.domain.vo.PaymentOrderVo;
import org.dromara.payment.security.StepUpVerificationMethod;
import org.dromara.payment.service.AccountMfaService;
import org.dromara.payment.service.AmountSlotService;
import org.dromara.payment.service.PaymentOrderService;
import org.dromara.payment.service.PaymentSensitiveOperationService;
import org.dromara.payment.service.PaymentTransactionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaymentSensitiveOperationControllerTest {

    @Test
    void forceMatchRequiresOperationBoundMfaToken() {
        PaymentOrderService orderService = mock(PaymentOrderService.class);
        AccountMfaService mfaService = mock(AccountMfaService.class);
        doThrow(new ServiceException("敏感操作需要 MFA 二次验证"))
            .when(mfaService)
            .requireStepUp(null, "PAYMENT_ORDER_FORCE_MATCH");
        PaymentOrderController controller =
            new PaymentOrderController(orderService, mfaService);
        ManualOrderMatchRequest request = new ManualOrderMatchRequest();
        request.setEventId(2L);
        request.setForce(true);

        assertThrows(
            ServiceException.class,
            () -> controller.manualMatch(1L, request, null));

        verify(orderService, never()).manualMatch(any(), any(), any());
    }

    @Test
    void forceMatchExecutesAfterMfaVerification() {
        PaymentOrderService orderService = mock(PaymentOrderService.class);
        AccountMfaService mfaService = mock(AccountMfaService.class);
        PaymentOrderVo order = new PaymentOrderVo();
        when(mfaService.requireStepUp(
            "step-up-token",
            "PAYMENT_ORDER_FORCE_MATCH"
        )).thenReturn(StepUpVerificationMethod.MFA);
        when(orderService.manualMatch(any(), any(), any())).thenReturn(order);
        PaymentOrderController controller =
            new PaymentOrderController(orderService, mfaService);
        ManualOrderMatchRequest request = new ManualOrderMatchRequest();
        request.setEventId(2L);
        request.setForce(true);

        controller.manualMatch(1L, request, "step-up-token");

        verify(mfaService).requireStepUp(
            "step-up-token",
            "PAYMENT_ORDER_FORCE_MATCH");
        verify(orderService).manualMatch(
            1L,
            request,
            StepUpVerificationMethod.MFA
        );
    }

    @Test
    void exactMatchWithoutForceDoesNotRequireMfa() {
        PaymentOrderService orderService = mock(PaymentOrderService.class);
        AccountMfaService mfaService = mock(AccountMfaService.class);
        PaymentOrderVo order = new PaymentOrderVo();
        when(orderService.manualMatch(any(), any(), any())).thenReturn(order);
        PaymentOrderController controller =
            new PaymentOrderController(orderService, mfaService);
        ManualOrderMatchRequest request = new ManualOrderMatchRequest();
        request.setEventId(2L);
        request.setForce(false);

        controller.manualMatch(1L, request, null);

        verify(mfaService, never()).requireStepUp(any(), any());
        verify(orderService).manualMatch(
            1L,
            request,
            StepUpVerificationMethod.SESSION
        );
    }

    @Test
    void reverseRequiresMfaBeforeExecution() {
        PaymentSensitiveOperationService sensitiveOperationService =
            mock(PaymentSensitiveOperationService.class);
        AccountMfaService mfaService = mock(AccountMfaService.class);
        doThrow(new ServiceException("敏感操作需要 MFA 二次验证"))
            .when(mfaService)
            .requireStepUp(null, "PAYMENT_CONFIRMATION_REVERSE");
        PaymentTransactionController controller =
            new PaymentTransactionController(
                mock(PaymentTransactionService.class),
                sensitiveOperationService,
                mock(AmountSlotService.class),
                mfaService);
        TransactionReverseRequest request = new TransactionReverseRequest();
        request.setReason("测试撤销");

        assertThrows(
            ServiceException.class,
            () -> controller.reverse(1L, request, null));

        verify(sensitiveOperationService, never())
            .reverseConfirmation(any(), any(), any());
    }

    @Test
    void reverseReturnsExecutedTransaction() {
        PaymentSensitiveOperationService sensitiveOperationService =
            mock(PaymentSensitiveOperationService.class);
        AccountMfaService mfaService = mock(AccountMfaService.class);
        PmPaymentTransaction transaction = new PmPaymentTransaction();
        transaction.setId(1L);
        when(mfaService.requireStepUp(
            "step-up-token",
            "PAYMENT_CONFIRMATION_REVERSE"
        )).thenReturn(StepUpVerificationMethod.MFA);
        when(sensitiveOperationService.reverseConfirmation(
            1L,
            "测试撤销",
            StepUpVerificationMethod.MFA
        ))
            .thenReturn(transaction);
        PaymentTransactionController controller =
            new PaymentTransactionController(
                mock(PaymentTransactionService.class),
                sensitiveOperationService,
                mock(AmountSlotService.class),
                mfaService);
        TransactionReverseRequest request = new TransactionReverseRequest();
        request.setReason("测试撤销");

        var response = controller.reverse(1L, request, "step-up-token");

        verify(mfaService).requireStepUp(
            "step-up-token",
            "PAYMENT_CONFIRMATION_REVERSE");
        verify(sensitiveOperationService)
            .reverseConfirmation(
                1L,
                "测试撤销",
                StepUpVerificationMethod.MFA
            );
        assert response.getData() == transaction;
    }

    @Test
    void reverseUsesSessionVerificationWhenMfaIsDisabled() {
        PaymentSensitiveOperationService sensitiveOperationService =
            mock(PaymentSensitiveOperationService.class);
        AccountMfaService mfaService = mock(AccountMfaService.class);
        when(mfaService.requireStepUp(
            null,
            "PAYMENT_CONFIRMATION_REVERSE"
        )).thenReturn(StepUpVerificationMethod.SESSION);
        PaymentTransactionController controller =
            new PaymentTransactionController(
                mock(PaymentTransactionService.class),
                sensitiveOperationService,
                mock(AmountSlotService.class),
                mfaService);
        TransactionReverseRequest request = new TransactionReverseRequest();
        request.setReason("会话确认撤销");

        controller.reverse(1L, request, null);

        verify(sensitiveOperationService).reverseConfirmation(
            1L,
            "会话确认撤销",
            StepUpVerificationMethod.SESSION
        );
    }
}
