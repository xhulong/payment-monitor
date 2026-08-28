package org.dromara.payment.service;

import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.PmPaymentTransaction;
import org.dromara.payment.domain.dto.ManualOrderMatchRequest;
import org.dromara.payment.domain.vo.PaymentOrderVo;
import org.dromara.payment.mapper.OrderMatchAuditMapper;
import org.dromara.payment.mapper.PaymentEventMapper;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.mapper.PaymentTransactionMapper;
import org.dromara.payment.mapper.QrAssetMapper;
import org.dromara.payment.security.StepUpVerificationMethod;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaymentOrderSensitiveOperationTest {

    @Test
    void mismatchedForceMatchExecutesAndConfirmsImmediatelyWithSensitiveLog() {
        Fixture fixture = fixture(false);
        execute(fixture, true, StepUpVerificationMethod.MFA);

        assertForceMatchCompleted(fixture, "MFA_SINGLE_CONFIRMATION");
        verify(fixture.logService).record(
            eq(1L),
            eq("FORCE_MATCH"),
            eq("PAYMENT_ORDER"),
            eq(21L),
            eq("迟到付款"),
            any(),
            eq("{\"before\":true}"),
            any(),
            eq(StepUpVerificationMethod.MFA),
            eq("FORCE_MATCH:21:31"));
    }

    @Test
    void exactForceMatchStillUsesMfaConfirmationAndSensitiveLog() {
        Fixture fixture = fixture(true);
        execute(fixture, true, StepUpVerificationMethod.MFA);

        assertForceMatchCompleted(fixture, "MFA_SINGLE_CONFIRMATION");
        verify(fixture.logService).record(
            eq(1L),
            eq("FORCE_MATCH"),
            eq("PAYMENT_ORDER"),
            eq(21L),
            eq("迟到付款"),
            any(),
            eq("{\"before\":true}"),
            any(),
            eq(StepUpVerificationMethod.MFA),
            eq("FORCE_MATCH:21:31"));
    }

    @Test
    void forceMatchWithoutMfaUsesSessionConfirmationAndSensitiveLog() {
        Fixture fixture = fixture(false);
        execute(fixture, true, StepUpVerificationMethod.SESSION);

        assertForceMatchCompleted(fixture, "SESSION_SINGLE_CONFIRMATION");
        verify(fixture.logService).record(
            eq(1L),
            eq("FORCE_MATCH"),
            eq("PAYMENT_ORDER"),
            eq(21L),
            eq("迟到付款"),
            any(),
            eq("{\"before\":true}"),
            any(),
            eq(StepUpVerificationMethod.SESSION),
            eq("FORCE_MATCH:21:31"));
    }

    @Test
    void exactMatchWithoutForceRemainsOrdinaryAndDoesNotWriteSensitiveLog() {
        Fixture fixture = fixture(true);
        execute(fixture, false, StepUpVerificationMethod.SESSION);

        assertEquals(PaymentConstants.ORDER_STATUS_PAID,
            fixture.order.getStatus());
        assertEquals(PaymentConstants.CONFIRMATION_NOTIFICATION,
            fixture.order.getConfirmationStatus());
        assertEquals("PAYMENT_NOTIFICATION",
            fixture.order.getConfirmationSource());
        assertEquals(PaymentConstants.TRANSACTION_MATCHED,
            fixture.transaction.getStatus());
        assertEquals(PaymentConstants.CONFIRMATION_NOTIFICATION,
            fixture.transaction.getConfirmationStatus());
        verify(fixture.logService, never()).snapshot(any());
        verify(fixture.logService, never()).record(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private Fixture fixture(boolean exact) {
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        PaymentEventMapper eventMapper = mock(PaymentEventMapper.class);
        PaymentTransactionMapper transactionMapper =
            mock(PaymentTransactionMapper.class);
        QrAssetMapper qrAssetMapper = mock(QrAssetMapper.class);
        OrderMatchAuditMapper auditMapper = mock(OrderMatchAuditMapper.class);
        AmountSlotService amountSlotService = mock(AmountSlotService.class);
        WebhookOutboxService webhook = mock(WebhookOutboxService.class);
        SensitiveOperationLogService logService =
            mock(SensitiveOperationLogService.class);

        PmPaymentOrder order = new PmPaymentOrder();
        order.setId(21L);
        order.setMerchantId(1L);
        order.setPlatform("WECHAT");
        order.setPayableAmountMinor(101L);
        order.setStatus(PaymentConstants.ORDER_STATUS_EXPIRED);
        order.setConfirmationStatus(PaymentConstants.CONFIRMATION_UNCONFIRMED);
        order.setVersion(2);
        when(orderMapper.selectById(21L)).thenReturn(order);
        when(orderMapper.selectByIdForUpdate(21L, 1L)).thenReturn(order);

        PmPaymentEvent event = new PmPaymentEvent();
        event.setId(31L);
        event.setMerchantId(1L);
        event.setPlatform(exact ? "WECHAT" : "ALIPAY");
        event.setDirection("INCOME");
        event.setAmountMinor(exact ? 101L : 100L);
        event.setCurrency("CNY");
        event.setStatus(PaymentConstants.EVENT_STATUS_RECEIVED);
        event.setEventTime(OffsetDateTime.now(ZoneOffset.UTC));
        event.setReceivedAt(event.getEventTime());
        when(eventMapper.selectByIdForUpdate(31L, 1L)).thenReturn(event);

        PmPaymentTransaction transaction = new PmPaymentTransaction();
        transaction.setId(41L);
        transaction.setMerchantId(1L);
        transaction.setEventId(31L);
        transaction.setPlatform("ALIPAY");
        transaction.setAmountMinor(100L);
        transaction.setStatus(PaymentConstants.TRANSACTION_OBSERVED);
        transaction.setConfirmationStatus(PaymentConstants.CONFIRMATION_UNCONFIRMED);
        transaction.setVersion(0);
        when(transactionMapper.selectByEventForUpdate(31L, 1L))
            .thenReturn(transaction);
        when(logService.snapshot(any())).thenReturn("{\"before\":true}");

        PaymentOrderVo resultVo = new PaymentOrderVo();
        resultVo.setId(21L);
        resultVo.setMerchantId(1L);
        when(orderMapper.selectVoOne(any())).thenReturn(resultVo);

        PaymentTransactionService transactionService =
            new PaymentTransactionService(
                transactionMapper,
                orderMapper,
                webhook,
                mock(MerchantDisplayService.class));
        PaymentProperties properties = new PaymentProperties();
        properties.setPublicBaseUrl("http://127.0.0.1:8080");
        PaymentOrderService service = new PaymentOrderService(
            orderMapper,
            qrAssetMapper,
            auditMapper,
            eventMapper,
            mock(QrAssetService.class),
            properties,
            webhook,
            transactionService,
            amountSlotService,
            logService,
            mock(MerchantLifecycleService.class),
            mock(org.dromara.payment.context.MerchantAccessService.class),
            mock(MerchantDisplayService.class));

        return new Fixture(
            order,
            transaction,
            amountSlotService,
            logService,
            service);
    }

    private void execute(
        Fixture fixture,
        boolean force,
        StepUpVerificationMethod verificationMethod
    ) {
        ManualOrderMatchRequest request = new ManualOrderMatchRequest();
        request.setEventId(31L);
        request.setForce(force);
        request.setNote("迟到付款");
        MerchantContext.set(1L, false);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isLogin).thenReturn(true);
            login.when(LoginHelper::getUserId).thenReturn(8L);
            fixture.service.manualMatch(21L, request, verificationMethod);
        } finally {
            MerchantContext.clear();
        }
    }

    private void assertForceMatchCompleted(
        Fixture fixture,
        String expectedConfirmationSource
    ) {
        assertEquals(PaymentConstants.ORDER_STATUS_PAID,
            fixture.order.getStatus());
        assertEquals(PaymentConstants.CONFIRMATION_MANUAL,
            fixture.order.getConfirmationStatus());
        assertEquals(expectedConfirmationSource,
            fixture.order.getConfirmationSource());
        assertEquals(PaymentConstants.TRANSACTION_CONFIRMED,
            fixture.transaction.getStatus());
        assertEquals(PaymentConstants.CONFIRMATION_MANUAL,
            fixture.transaction.getConfirmationStatus());
        verify(fixture.amountSlotService).startCooling(21L);
    }

    private record Fixture(
        PmPaymentOrder order,
        PmPaymentTransaction transaction,
        AmountSlotService amountSlotService,
        SensitiveOperationLogService logService,
        PaymentOrderService service
    ) {
    }
}
