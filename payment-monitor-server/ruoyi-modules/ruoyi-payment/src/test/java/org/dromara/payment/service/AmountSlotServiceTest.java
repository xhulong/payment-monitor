package org.dromara.payment.service;

import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.PmAmountSlotReservation;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.mapper.AmountSlotMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class AmountSlotServiceTest {

    @Test
    void coolingSlotCannotBeReusedBeforeDeadline() {
        AmountSlotMapper mapper = mock(AmountSlotMapper.class);
        PmAmountSlotReservation slot = slot(PaymentConstants.SLOT_COOLING);
        slot.setCoolingUntil(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        when(mapper.selectKeyForUpdate(1L, "WECHAT", 101L)).thenReturn(slot);

        assertFalse(new AmountSlotService(
            mapper,
            mock(MerchantDisplayService.class)).reserve(order(99L)));
    }

    @Test
    void expiredCoolingSlotCanBeReservedByAnotherOrder() {
        AmountSlotMapper mapper = mock(AmountSlotMapper.class);
        PmAmountSlotReservation slot = slot(PaymentConstants.SLOT_COOLING);
        slot.setCoolingUntil(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        when(mapper.selectKeyForUpdate(1L, "WECHAT", 101L)).thenReturn(slot);

        assertTrue(new AmountSlotService(
            mapper,
            mock(MerchantDisplayService.class)).reserve(order(99L)));
        verify(mapper).updateById(slot);
        assertTrue(PaymentConstants.SLOT_ACTIVE.equals(slot.getStatus()));
        assertTrue(slot.getOrderId().equals(99L));
    }

    @Test
    void releasedSlotCanBeReservedByAnotherOrder() {
        AmountSlotMapper mapper = mock(AmountSlotMapper.class);
        PmAmountSlotReservation slot = slot(PaymentConstants.SLOT_RELEASED);
        slot.setReleasedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(mapper.selectKeyForUpdate(1L, "WECHAT", 101L)).thenReturn(slot);

        assertTrue(new AmountSlotService(
            mapper,
            mock(MerchantDisplayService.class)).reserve(order(99L)));
        verify(mapper).updateById(slot);
        assertTrue(PaymentConstants.SLOT_ACTIVE.equals(slot.getStatus()));
        assertTrue(slot.getOrderId().equals(99L));
    }

    @Test
    void terminalOrderStartsTenMinuteCoolingWindow() {
        AmountSlotMapper mapper = mock(AmountSlotMapper.class);
        AmountSlotService service = new AmountSlotService(
            mapper,
            mock(MerchantDisplayService.class));

        service.startCooling(88L);

        ArgumentCaptor<OffsetDateTime> coolingUntil = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> now = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(mapper).startCooling(eq(88L), coolingUntil.capture(), now.capture());
        long seconds = java.time.Duration.between(now.getValue(), coolingUntil.getValue()).toSeconds();
        assertTrue(seconds == AmountSlotService.COOLING_SECONDS);
    }

    private PmAmountSlotReservation slot(String status) {
        PmAmountSlotReservation slot = new PmAmountSlotReservation();
        slot.setId(10L);
        slot.setMerchantId(1L);
        slot.setPlatform("WECHAT");
        slot.setPayableAmountMinor(101L);
        slot.setOrderId(20L);
        slot.setStatus(status);
        slot.setVersion(0);
        return slot;
    }

    private PmPaymentOrder order(Long id) {
        PmPaymentOrder order = new PmPaymentOrder();
        order.setId(id);
        order.setMerchantId(1L);
        order.setPlatform("WECHAT");
        order.setPayableAmountMinor(101L);
        return order;
    }
}
