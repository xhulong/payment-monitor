package org.dromara.payment.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.PmPaymentTransaction;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.mapper.PaymentTransactionMapper;
import org.dromara.payment.security.StepUpVerificationMethod;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaymentTransactionServiceTest {

    @Test
    void incomeEventCreatesObservedTransactionAndBusinessEvent() {
        PaymentTransactionMapper mapper = mock(PaymentTransactionMapper.class);
        WebhookOutboxService webhook = mock(WebhookOutboxService.class);
        when(mapper.insertOnConflict(any())).thenReturn(1);
        PaymentTransactionService service = new PaymentTransactionService(
            mapper,
            mock(PaymentOrderMapper.class),
            webhook,
            mock(MerchantDisplayService.class)
        );
        PmPaymentEvent event = incomeEvent();

        PmPaymentTransaction result = service.observe(event);

        ArgumentCaptor<PmPaymentTransaction> captor =
            ArgumentCaptor.forClass(PmPaymentTransaction.class);
        verify(mapper).insertOnConflict(captor.capture());
        assertEquals(PaymentConstants.TRANSACTION_OBSERVED, result.getStatus());
        assertEquals(PaymentConstants.CONFIRMATION_UNCONFIRMED,
            result.getConfirmationStatus());
        assertEquals(123L, result.getAmountMinor());
        verify(webhook).enqueueTransactionObserved(result, event);
    }

    @Test
    void expenseEventDoesNotCreatePaymentTransaction() {
        PaymentTransactionMapper mapper = mock(PaymentTransactionMapper.class);
        PaymentTransactionService service = new PaymentTransactionService(
            mapper,
            mock(PaymentOrderMapper.class),
            mock(WebhookOutboxService.class),
            mock(MerchantDisplayService.class)
        );
        PmPaymentEvent event = incomeEvent();
        event.setDirection("EXPENSE");

        assertNull(service.observe(event));
        verify(mapper, never()).insertOnConflict(any());
    }

    @Test
    void repeatedReconciliationIsIdempotent() {
        PaymentTransactionMapper mapper = mock(PaymentTransactionMapper.class);
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        WebhookOutboxService webhook = mock(WebhookOutboxService.class);
        PaymentTransactionService service = new PaymentTransactionService(
            mapper,
            orderMapper,
            webhook,
            mock(MerchantDisplayService.class)
        );
        PmPaymentTransaction transaction = new PmPaymentTransaction();
        transaction.setStatus(PaymentConstants.TRANSACTION_RECONCILED);
        transaction.setConfirmationStatus(PaymentConstants.CONFIRMATION_RECONCILED);
        PmPaymentOrder order = new PmPaymentOrder();
        order.setConfirmationStatus(PaymentConstants.CONFIRMATION_RECONCILED);

        service.markReconciled(transaction, order);

        verify(mapper, never()).updateById(any(PmPaymentTransaction.class));
        verify(orderMapper, never()).updateById(any(PmPaymentOrder.class));
        verify(webhook, never()).enqueueOrderEvent(any(), any(), any());
    }

    @Test
    void mismatchedAmountCannotBeConfirmedBySingleReviewer() {
        PaymentTransactionMapper mapper = mock(PaymentTransactionMapper.class);
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        PaymentTransactionService service = new PaymentTransactionService(
            mapper,
            orderMapper,
            mock(WebhookOutboxService.class),
            mock(MerchantDisplayService.class)
        );
        PmPaymentTransaction transaction = new PmPaymentTransaction();
        transaction.setId(31L);
        transaction.setMerchantId(1L);
        transaction.setOrderId(41L);
        transaction.setPlatform("WECHAT");
        transaction.setAmountMinor(101L);
        transaction.setStatus(PaymentConstants.TRANSACTION_MATCHED);
        transaction.setConfirmationStatus(PaymentConstants.CONFIRMATION_NOTIFICATION);
        PmPaymentOrder order = new PmPaymentOrder();
        order.setId(41L);
        order.setMerchantId(1L);
        order.setPlatform("WECHAT");
        order.setPayableAmountMinor(102L);
        order.setStatus(PaymentConstants.ORDER_STATUS_PAID);
        when(mapper.selectByIdForUpdate(31L, 1L)).thenReturn(transaction);
        when(orderMapper.selectByIdForUpdate(41L, 1L)).thenReturn(order);

        MerchantContext.set(1L, false);
        try {
            assertThrows(ServiceException.class, () -> service.confirm(31L, null));
        } finally {
            MerchantContext.clear();
        }

        verify(mapper, never()).updateById(any(PmPaymentTransaction.class));
        verify(orderMapper, never()).updateById(any(PmPaymentOrder.class));
    }

    @Test
    void mfaSingleConfirmationMarksTransactionAndOrderManual() {
        PaymentTransactionMapper mapper = mock(PaymentTransactionMapper.class);
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        WebhookOutboxService webhook = mock(WebhookOutboxService.class);
        PaymentTransactionService service = new PaymentTransactionService(
            mapper,
            orderMapper,
            webhook,
            mock(MerchantDisplayService.class)
        );
        PmPaymentTransaction transaction = new PmPaymentTransaction();
        transaction.setId(51L);
        transaction.setOrderId(61L);
        transaction.setStatus(PaymentConstants.TRANSACTION_MATCHED);
        transaction.setConfirmationStatus(PaymentConstants.CONFIRMATION_NOTIFICATION);
        transaction.setVersion(1);
        PmPaymentOrder order = new PmPaymentOrder();
        order.setId(61L);
        order.setConfirmationStatus(PaymentConstants.CONFIRMATION_NOTIFICATION);
        order.setVersion(2);

        service.markConfirmedBySensitiveOperation(
            transaction,
            order,
            8L,
            "MFA 确认",
            StepUpVerificationMethod.MFA
        );

        assertEquals(PaymentConstants.TRANSACTION_CONFIRMED, transaction.getStatus());
        assertEquals(PaymentConstants.CONFIRMATION_MANUAL,
            transaction.getConfirmationStatus());
        assertEquals(PaymentConstants.CONFIRMATION_MANUAL,
            order.getConfirmationStatus());
        assertEquals("MFA_SINGLE_CONFIRMATION", order.getConfirmationSource());
        assertEquals(8L, order.getConfirmedBy());
        verify(mapper).updateById(transaction);
        verify(orderMapper).updateById(order);
        verify(webhook).enqueueOrderEvent(order, null, "payment.order.confirmed");
    }

    @Test
    void sessionSingleConfirmationUsesSessionSource() {
        PaymentTransactionService service = new PaymentTransactionService(
            mock(PaymentTransactionMapper.class),
            mock(PaymentOrderMapper.class),
            mock(WebhookOutboxService.class),
            mock(MerchantDisplayService.class)
        );
        PmPaymentTransaction transaction = new PmPaymentTransaction();
        transaction.setId(51L);
        transaction.setOrderId(61L);
        transaction.setStatus(PaymentConstants.TRANSACTION_MATCHED);
        transaction.setConfirmationStatus(PaymentConstants.CONFIRMATION_NOTIFICATION);
        transaction.setVersion(1);
        PmPaymentOrder order = new PmPaymentOrder();
        order.setId(61L);
        order.setConfirmationStatus(PaymentConstants.CONFIRMATION_NOTIFICATION);
        order.setVersion(2);

        service.markConfirmedBySensitiveOperation(
            transaction,
            order,
            8L,
            "会话确认",
            StepUpVerificationMethod.SESSION
        );

        assertEquals("SESSION_SINGLE_CONFIRMATION", order.getConfirmationSource());
    }

    private PmPaymentEvent incomeEvent() {
        PmPaymentEvent event = new PmPaymentEvent();
        event.setId(9L);
        event.setMerchantId(1L);
        event.setPlatform("WECHAT");
        event.setDirection("INCOME");
        event.setAmountMinor(123L);
        event.setCurrency("CNY");
        event.setEventTime(OffsetDateTime.now(ZoneOffset.UTC));
        event.setReceivedAt(event.getEventTime());
        return event;
    }
}
