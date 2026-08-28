package org.dromara.payment.service;

import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.PmPaymentTransaction;
import org.dromara.payment.domain.PmReconciliationRun;
import org.dromara.payment.mapper.MerchantMapper;
import org.dromara.payment.mapper.PaymentEventMapper;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.mapper.PaymentTransactionMapper;
import org.dromara.payment.mapper.ReconciliationItemMapper;
import org.dromara.payment.mapper.ReconciliationRunMapper;
import org.dromara.payment.mapper.WebhookOutboxMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaymentReconciliationServiceTest {

    @Test
    void previewHighlightsUnmatchedIncomeAndAmountDifference() {
        MerchantMapper merchantMapper = mock(MerchantMapper.class);
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        PaymentEventMapper eventMapper = mock(PaymentEventMapper.class);
        WebhookOutboxMapper outboxMapper = mock(WebhookOutboxMapper.class);
        PmMerchant merchant = new PmMerchant();
        merchant.setId(7L);
        merchant.setTimezone("Asia/Shanghai");
        when(merchantMapper.selectById(7L)).thenReturn(merchant);
        PmPaymentOrder paid = new PmPaymentOrder();
        paid.setPayableAmountMinor(100L);
        when(orderMapper.selectList(any())).thenReturn(List.of(paid));
        PmPaymentTransaction matched = new PmPaymentTransaction();
        matched.setOrderId(10L);
        matched.setAmountMinor(100L);
        PmPaymentTransaction unmatched = new PmPaymentTransaction();
        unmatched.setAmountMinor(50L);
        PaymentTransactionMapper transactionMapper = mock(PaymentTransactionMapper.class);
        when(transactionMapper.selectList(any())).thenReturn(List.of(matched, unmatched));
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(eventMapper.selectCount(any())).thenReturn(0L);
        when(outboxMapper.selectCount(any())).thenReturn(0L);
        PaymentReconciliationService service = new PaymentReconciliationService(
            mock(ReconciliationRunMapper.class),
            mock(ReconciliationItemMapper.class),
            merchantMapper,
            orderMapper,
            eventMapper,
            transactionMapper,
            outboxMapper,
            mock(PaymentTransactionService.class),
            mock(org.dromara.payment.context.MerchantAccessService.class),
            mock(MerchantDisplayService.class));

        PmReconciliationRun result = service.previewToday(7L);

        assertEquals("ATTENTION_REQUIRED", result.getStatus());
        assertEquals(1L, result.getPaidOrderCount());
        assertEquals(1L, result.getUnmatchedIncomeCount());
        assertEquals(50L, result.getUnmatchedIncomeAmountMinor());
        assertEquals(0L, result.getAmountDifferenceMinor());
    }
}
