package org.dromara.payment.service;

import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.PmPaymentTransaction;
import org.dromara.payment.domain.PmOrderMatchAudit;
import org.dromara.payment.mapper.OrderMatchAuditMapper;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.mapper.PaymentTransactionMapper;
import org.dromara.payment.security.StepUpVerificationMethod;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaymentSensitiveOperationServiceTest {

    @Test
    void mfaConfirmedReverseExecutesImmediatelyAndRecordsSnapshot() {
        PaymentTransactionMapper transactionMapper =
            mock(PaymentTransactionMapper.class);
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        OrderMatchAuditMapper auditMapper = mock(OrderMatchAuditMapper.class);
        AmountSlotService amountSlotService = mock(AmountSlotService.class);
        WebhookOutboxService webhookOutboxService =
            mock(WebhookOutboxService.class);
        SensitiveOperationLogService logService =
            mock(SensitiveOperationLogService.class);

        PmPaymentTransaction transaction = new PmPaymentTransaction();
        transaction.setId(11L);
        transaction.setMerchantId(1L);
        transaction.setOrderId(22L);
        transaction.setStatus(PaymentConstants.TRANSACTION_CONFIRMED);
        transaction.setConfirmationStatus(PaymentConstants.CONFIRMATION_MANUAL);
        transaction.setVersion(3);
        when(transactionMapper.selectById(11L)).thenReturn(transaction);
        when(transactionMapper.selectByIdForUpdate(11L, 1L))
            .thenReturn(transaction);

        PmPaymentOrder order = new PmPaymentOrder();
        order.setId(22L);
        order.setMerchantId(1L);
        order.setStatus(PaymentConstants.ORDER_STATUS_PAID);
        order.setConfirmationStatus(PaymentConstants.CONFIRMATION_MANUAL);
        order.setVersion(2);
        when(orderMapper.selectByIdForUpdate(22L, 1L)).thenReturn(order);
        when(logService.snapshot(any())).thenReturn("{\"before\":true}");

        PaymentSensitiveOperationService service =
            new PaymentSensitiveOperationService(
                transactionMapper,
                orderMapper,
                auditMapper,
                amountSlotService,
                webhookOutboxService,
                logService);

        MerchantContext.set(1L, false);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(8L);
            PmPaymentTransaction result =
                service.reverseConfirmation(
                    11L,
                    "确认来源错误",
                    StepUpVerificationMethod.MFA
                );
            assertEquals(transaction, result);
        } finally {
            MerchantContext.clear();
        }

        assertEquals(PaymentConstants.TRANSACTION_REVERSED,
            transaction.getStatus());
        assertEquals(PaymentConstants.CONFIRMATION_UNCONFIRMED,
            transaction.getConfirmationStatus());
        assertEquals(PaymentConstants.ORDER_STATUS_CONFLICT, order.getStatus());
        assertEquals(PaymentConstants.CONFIRMATION_UNCONFIRMED,
            order.getConfirmationStatus());
        assertEquals("CONFIRMATION_REVOKED", order.getConfirmationSource());
        verify(amountSlotService).reactivate(22L);
        verify(webhookOutboxService).enqueueOrderEvent(
            order,
            null,
            "payment.order.confirmation_revoked");
        verify(logService).record(
            eq(1L),
            eq("REVERSE_CONFIRMATION"),
            eq("PAYMENT_TRANSACTION"),
            eq(11L),
            eq("确认来源错误"),
            eq(Map.of("transactionId", 11L, "reason", "确认来源错误")),
            eq("{\"before\":true}"),
            any(),
            eq(StepUpVerificationMethod.MFA),
            eq("REVERSE_CONFIRMATION:11:3"));
    }

    @Test
    void sessionConfirmedReverseRecordsSessionVerificationAndAuditNote() {
        PaymentTransactionMapper transactionMapper =
            mock(PaymentTransactionMapper.class);
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        OrderMatchAuditMapper auditMapper = mock(OrderMatchAuditMapper.class);
        SensitiveOperationLogService logService =
            mock(SensitiveOperationLogService.class);

        PmPaymentTransaction transaction = new PmPaymentTransaction();
        transaction.setId(11L);
        transaction.setMerchantId(1L);
        transaction.setOrderId(22L);
        transaction.setStatus(PaymentConstants.TRANSACTION_CONFIRMED);
        transaction.setConfirmationStatus(PaymentConstants.CONFIRMATION_MANUAL);
        transaction.setVersion(3);
        when(transactionMapper.selectById(11L)).thenReturn(transaction);
        when(transactionMapper.selectByIdForUpdate(11L, 1L))
            .thenReturn(transaction);

        PmPaymentOrder order = new PmPaymentOrder();
        order.setId(22L);
        order.setMerchantId(1L);
        order.setStatus(PaymentConstants.ORDER_STATUS_PAID);
        order.setConfirmationStatus(PaymentConstants.CONFIRMATION_MANUAL);
        order.setVersion(2);
        when(orderMapper.selectByIdForUpdate(22L, 1L)).thenReturn(order);
        when(logService.snapshot(any())).thenReturn("{\"before\":true}");

        PaymentSensitiveOperationService service =
            new PaymentSensitiveOperationService(
                transactionMapper,
                orderMapper,
                auditMapper,
                mock(AmountSlotService.class),
                mock(WebhookOutboxService.class),
                logService);

        MerchantContext.set(1L, false);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(8L);
            service.reverseConfirmation(
                11L,
                "会话确认撤销",
                StepUpVerificationMethod.SESSION
            );
        } finally {
            MerchantContext.clear();
        }

        verify(logService).record(
            eq(1L),
            eq("REVERSE_CONFIRMATION"),
            eq("PAYMENT_TRANSACTION"),
            eq(11L),
            eq("会话确认撤销"),
            any(),
            eq("{\"before\":true}"),
            any(),
            eq(StepUpVerificationMethod.SESSION),
            eq("REVERSE_CONFIRMATION:11:3"));
        ArgumentCaptor<PmOrderMatchAudit> auditCaptor =
            ArgumentCaptor.forClass(PmOrderMatchAudit.class);
        verify(auditMapper).insert(auditCaptor.capture());
        assertEquals(
            "登录会话确认撤销支付确认：会话确认撤销",
            auditCaptor.getValue().getNote()
        );
    }

    @Test
    void repeatedReverseReturnsCurrentTransactionWithoutDuplicateSideEffects() {
        PaymentTransactionMapper transactionMapper =
            mock(PaymentTransactionMapper.class);
        PmPaymentTransaction transaction = new PmPaymentTransaction();
        transaction.setId(11L);
        transaction.setMerchantId(1L);
        transaction.setStatus(PaymentConstants.TRANSACTION_REVERSED);
        when(transactionMapper.selectById(11L)).thenReturn(transaction);
        when(transactionMapper.selectByIdForUpdate(11L, 1L))
            .thenReturn(transaction);
        SensitiveOperationLogService logService =
            mock(SensitiveOperationLogService.class);
        WebhookOutboxService webhook = mock(WebhookOutboxService.class);

        PaymentSensitiveOperationService service =
            new PaymentSensitiveOperationService(
                transactionMapper,
                mock(PaymentOrderMapper.class),
                mock(OrderMatchAuditMapper.class),
                mock(AmountSlotService.class),
                webhook,
                logService);

        MerchantContext.set(1L, false);
        try {
            service.reverseConfirmation(
                11L,
                "重复请求",
                StepUpVerificationMethod.MFA
            );
        } finally {
            MerchantContext.clear();
        }

        verify(transactionMapper, never()).updateById(
            any(PmPaymentTransaction.class));
        verify(webhook, never()).enqueueOrderEvent(any(), any(), any());
        verify(logService, never()).record(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
