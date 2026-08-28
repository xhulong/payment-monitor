package org.dromara.payment.service;

import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.dto.PaymentEventBatchRequest;
import org.dromara.payment.domain.dto.PaymentEventItem;
import org.dromara.payment.domain.vo.PaymentEventBatchVo;
import org.dromara.payment.event.PaymentIncomeReceivedEvent;
import org.dromara.payment.mapper.PaymentEventMapper;
import org.dromara.payment.mapper.PaymentEventReviewMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaymentEventServiceTest {

    @Test
    void reportsAcceptedAndDuplicateEventsIndividually() {
        PaymentEventMapper mapper = mock(PaymentEventMapper.class);
        PaymentDeviceService deviceService = mock(PaymentDeviceService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);
        PaymentProperties properties = new PaymentProperties();
        when(mapper.insertOnConflict(any(PmPaymentEvent.class))).thenReturn(1, 0);
        PaymentEventService service = new PaymentEventService(
            mapper,
            mock(PaymentEventReviewMapper.class),
            mock(PaymentOrderService.class),
            mock(PaymentTransactionService.class),
            deviceService,
            mock(MerchantAccessService.class),
            objectMapper,
            properties,
            eventPublisher,
            mock(MerchantDisplayService.class)
        );

        PaymentEventBatchRequest request = batch(
            event("event-1", "INCOME"),
            event("event-2", "INCOME")
        );
        PaymentEventBatchVo result = service.ingest(1L, 2L, request);

        assertEquals(List.of("event-1"), result.getAccepted());
        assertEquals(List.of("event-2"), result.getDuplicates());
        assertEquals(0, result.getRejected().size());
        verify(eventPublisher, times(1))
            .publishEvent(any(PaymentIncomeReceivedEvent.class));
    }

    @Test
    void rejectsInvalidAmountWithoutFailingOtherItems() {
        PaymentEventMapper mapper = mock(PaymentEventMapper.class);
        PaymentDeviceService deviceService = mock(PaymentDeviceService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);
        PaymentProperties properties = new PaymentProperties();
        PaymentEventService service = new PaymentEventService(
            mapper,
            mock(PaymentEventReviewMapper.class),
            mock(PaymentOrderService.class),
            mock(PaymentTransactionService.class),
            deviceService,
            mock(MerchantAccessService.class),
            objectMapper,
            properties,
            eventPublisher,
            mock(MerchantDisplayService.class)
        );

        PaymentEventItem item = event("invalid-amount", "INCOME");
        item.setAmountMinor(0L);
        PaymentEventBatchVo result = service.ingest(
            1L,
            2L,
            batch(item)
        );

        assertEquals(0, result.getAccepted().size());
        assertEquals(1, result.getRejected().size());
        assertEquals(
            "invalid-amount",
            result.getRejected().getFirst().getClientEventId()
        );
    }

    @Test
    void preservesExactMillisecondTimelineAndPublishesIncomeOnlyOnce() {
        PaymentEventMapper mapper = mock(PaymentEventMapper.class);
        PaymentDeviceService deviceService = mock(PaymentDeviceService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);
        PaymentProperties properties = new PaymentProperties();
        when(mapper.insertOnConflict(any(PmPaymentEvent.class))).thenReturn(1);
        PaymentEventService service = new PaymentEventService(
            mapper,
            mock(PaymentEventReviewMapper.class),
            mock(PaymentOrderService.class),
            mock(PaymentTransactionService.class),
            deviceService,
            mock(MerchantAccessService.class),
            objectMapper,
            properties,
            eventPublisher,
            mock(MerchantDisplayService.class)
        );
        PaymentEventBatchRequest request = batch(
            event("millisecond-event", "INCOME")
        );

        PaymentEventBatchVo result = service.ingest(1L, 2L, request);

        assertEquals(List.of("millisecond-event"), result.getAccepted());
        ArgumentCaptor<PmPaymentEvent> entityCaptor =
            ArgumentCaptor.forClass(PmPaymentEvent.class);
        verify(mapper).insertOnConflict(entityCaptor.capture());
        PmPaymentEvent stored = entityCaptor.getValue();
        assertEquals(
            Instant.parse("2026-07-16T06:30:00.123Z").toEpochMilli(),
            stored.getEventTimeMs()
        );
        assertEquals(
            Instant.parse("2026-07-16T06:30:00.135Z").toEpochMilli(),
            stored.getClientReceivedAtMs()
        );
        assertEquals(
            Instant.parse("2026-07-16T06:30:01.234Z").toEpochMilli(),
            stored.getClientSentAtMs()
        );
        assertNotNull(stored.getReceivedAt());

        ArgumentCaptor<Object> eventCaptor =
            ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PaymentIncomeReceivedEvent incomeEvent =
            (PaymentIncomeReceivedEvent) eventCaptor.getValue();
        assertEquals(stored.getEventTimeMs(), incomeEvent.eventTimeMs());
        assertEquals(
            stored.getClientReceivedAtMs(),
            incomeEvent.clientReceivedAtMs()
        );
    }

    @Test
    void doesNotPublishInternalEventForExpense() {
        PaymentEventMapper mapper = mock(PaymentEventMapper.class);
        PaymentDeviceService deviceService = mock(PaymentDeviceService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);
        PaymentProperties properties = new PaymentProperties();
        when(mapper.insertOnConflict(any(PmPaymentEvent.class))).thenReturn(1);
        PaymentEventService service = new PaymentEventService(
            mapper,
            mock(PaymentEventReviewMapper.class),
            mock(PaymentOrderService.class),
            mock(PaymentTransactionService.class),
            deviceService,
            mock(MerchantAccessService.class),
            objectMapper,
            properties,
            eventPublisher,
            mock(MerchantDisplayService.class)
        );

        PaymentEventBatchVo result = service.ingest(
            1L,
            2L,
            batch(event("expense-event", "EXPENSE"))
        );

        assertEquals(List.of("expense-event"), result.getAccepted());
        verify(eventPublisher, times(0)).publishEvent(any());
    }

    @Test
    void acceptsLegacyProtocolOneEventWithoutMillisecondFields() {
        PaymentEventMapper mapper = mock(PaymentEventMapper.class);
        PaymentDeviceService deviceService = mock(PaymentDeviceService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);
        PaymentProperties properties = new PaymentProperties();
        when(mapper.insertOnConflict(any(PmPaymentEvent.class))).thenReturn(1);
        PaymentEventService service = new PaymentEventService(
            mapper,
            mock(PaymentEventReviewMapper.class),
            mock(PaymentOrderService.class),
            mock(PaymentTransactionService.class),
            deviceService,
            mock(MerchantAccessService.class),
            objectMapper,
            properties,
            eventPublisher,
            mock(MerchantDisplayService.class)
        );
        PaymentEventItem legacyItem = event("legacy-event", "INCOME");
        legacyItem.setEventTimeMs(null);
        legacyItem.setClientReceivedAt(null);
        legacyItem.setClientReceivedAtMs(null);

        PaymentEventBatchVo result = service.ingest(
            1L,
            2L,
            batch(legacyItem)
        );

        assertEquals(List.of("legacy-event"), result.getAccepted());
        ArgumentCaptor<PmPaymentEvent> entityCaptor =
            ArgumentCaptor.forClass(PmPaymentEvent.class);
        verify(mapper).insertOnConflict(entityCaptor.capture());
        PmPaymentEvent stored = entityCaptor.getValue();
        long expected = legacyItem.getEventTime().toInstant().toEpochMilli();
        assertEquals(expected, stored.getEventTimeMs());
        assertEquals(expected, stored.getClientReceivedAtMs());
        assertEquals(stored.getEventTime(), stored.getClientReceivedAt());
    }

    @Test
    void marksLaterCrossDeviceEventAsSuspectedWithoutRejectingIt() {
        PaymentEventMapper mapper = mock(PaymentEventMapper.class);
        PaymentDeviceService deviceService = mock(PaymentDeviceService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        PaymentOrderService orderService = mock(PaymentOrderService.class);
        when(mapper.insertOnConflict(any(PmPaymentEvent.class))).thenReturn(1);
        PmPaymentEvent first = new PmPaymentEvent();
        first.setId(900L);
        first.setDeviceId(10L);
        first.setMerchantId(1L);
        when(mapper.selectOne(any())).thenReturn(first);
        PaymentEventService service = new PaymentEventService(
            mapper,
            mock(PaymentEventReviewMapper.class),
            orderService,
            mock(PaymentTransactionService.class),
            deviceService,
            mock(MerchantAccessService.class),
            mock(ObjectMapper.class),
            new PaymentProperties(),
            publisher,
            mock(MerchantDisplayService.class)
        );

        PaymentEventBatchVo result = service.ingest(
            1L,
            20L,
            batch(event("cross-device-duplicate", "INCOME"))
        );

        assertEquals(List.of("cross-device-duplicate"), result.getAccepted());
        ArgumentCaptor<PmPaymentEvent> updated =
            ArgumentCaptor.forClass(PmPaymentEvent.class);
        verify(mapper).updateById(updated.capture());
        assertEquals(PaymentConstants.DUPLICATE_STATUS_SUSPECTED,
            updated.getValue().getDuplicateStatus());
        assertEquals(900L, updated.getValue().getDuplicateOfEventId());
        verify(orderService).autoMatch(any(PmPaymentEvent.class));
    }

    @Test
    void persistsDeviceSequenceWithoutChangingProtocolVersion() {
        PaymentEventMapper mapper = mock(PaymentEventMapper.class);
        when(mapper.insertOnConflict(any(PmPaymentEvent.class))).thenReturn(1);
        PaymentTransactionService transactionService = mock(PaymentTransactionService.class);
        PaymentEventService service = new PaymentEventService(
            mapper,
            mock(PaymentEventReviewMapper.class),
            mock(PaymentOrderService.class),
            transactionService,
            mock(PaymentDeviceService.class),
            mock(MerchantAccessService.class),
            mock(ObjectMapper.class),
            new PaymentProperties(),
            mock(ApplicationEventPublisher.class),
            mock(MerchantDisplayService.class)
        );
        PaymentEventItem item = event("sequenced-event", "INCOME");
        item.setDeviceSequence(42L);

        service.ingest(1L, 2L, batch(item));

        ArgumentCaptor<PmPaymentEvent> eventCaptor =
            ArgumentCaptor.forClass(PmPaymentEvent.class);
        verify(mapper).insertOnConflict(eventCaptor.capture());
        assertEquals(42L, eventCaptor.getValue().getDeviceSequence());
        verify(transactionService).observe(eventCaptor.getValue());
    }

    private PaymentEventBatchRequest batch(PaymentEventItem... items) {
        PaymentEventBatchRequest request = new PaymentEventBatchRequest();
        request.setSentAt(
            OffsetDateTime.parse("2026-07-16T06:30:01.234Z")
        );
        request.setEvents(List.of(items));
        return request;
    }

    private PaymentEventItem event(
        String clientEventId,
        String direction
    ) {
        PaymentEventItem item = new PaymentEventItem();
        item.setClientEventId(clientEventId);
        item.setPlatform("WECHAT");
        item.setDirection(direction);
        item.setAmountMinor(10001L);
        item.setCurrency("CNY");

        long eventTimeMs =
            Instant.parse("2026-07-16T06:30:00.123Z").toEpochMilli();
        item.setEventTime(
            OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(eventTimeMs),
                ZoneOffset.UTC
            )
        );
        item.setEventTimeMs(eventTimeMs);

        long clientReceivedAtMs = eventTimeMs + 12;
        item.setClientReceivedAt(
            OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(clientReceivedAtMs),
                ZoneOffset.UTC
            )
        );
        item.setClientReceivedAtMs(clientReceivedAtMs);
        item.setParseStatus("PARSED");
        item.setParserVersion("1.0.0");
        item.setMatchedRule("wechat_income:成功收款");
        item.setFingerprint("a".repeat(64));
        item.setRawHash("b".repeat(64));
        return item;
    }
}
