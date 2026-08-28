package org.dromara.payment.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.session.SqlSession;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.api.MerchantApiException;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmAmountSlotReservation;
import org.dromara.payment.domain.PmOrderMatchAudit;
import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.PmPaymentTransaction;
import org.dromara.payment.domain.dto.ManualOrderMatchRequest;
import org.dromara.payment.domain.dto.MerchantOrderCreateRequest;
import org.dromara.payment.domain.dto.WebhookResolutionRequest;
import org.dromara.payment.mapper.*;
import org.dromara.payment.security.DeviceSecretCipher;
import org.dromara.payment.security.StepUpVerificationMethod;
import org.dromara.payment.security.WebhookUrlValidator;
import org.dromara.payment.service.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class OrderWebhookContainerTest {

    private static final long MERCHANT_A = 6101L;
    private static final long MERCHANT_B = 6201L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("payment_monitor")
            .withUsername("payment_monitor")
            .withPassword("payment_monitor_test");

    @Test
    void reservesIsolatedAmountSlotsAndRunsWebhookLifecycle() throws Exception {
        DataSource dataSource =
            PaymentPostgresTestSupport.migrateLatest(POSTGRES, "orders_webhooks");
        insertFixtures(dataSource);
        var sessionFactory = PaymentPostgresTestSupport.sqlSessionFactory(dataSource);

        try (SqlSession session = sessionFactory.openSession(true)) {
            PaymentOrderMapper orderMapper = session.getMapper(PaymentOrderMapper.class);
            QrAssetMapper qrAssetMapper = session.getMapper(QrAssetMapper.class);
            AmountSlotMapper amountSlotMapper = session.getMapper(AmountSlotMapper.class);
            WebhookOutboxMapper outboxMapper = session.getMapper(WebhookOutboxMapper.class);
            WebhookEndpointMapper endpointMapper = session.getMapper(WebhookEndpointMapper.class);

            MerchantLifecycleService lifecycle = mock(MerchantLifecycleService.class);
            when(lifecycle.requireActive(anyLong())).thenAnswer(invocation -> {
                var merchant = new org.dromara.payment.domain.PmMerchant();
                merchant.setId(invocation.getArgument(0));
                merchant.setLifecycleStatus("ACTIVE");
                return merchant;
            });
            QrAssetService qrAssetService = new QrAssetService(
                    qrAssetMapper,
                    orderMapper,
                    mock(org.dromara.payment.context.MerchantAccessService.class),
                    mock(org.dromara.payment.service.MerchantDisplayService.class));
            AmountSlotService amountSlotService = new AmountSlotService(
                amountSlotMapper,
                mock(org.dromara.payment.service.MerchantDisplayService.class));
            PaymentProperties properties = new PaymentProperties();
            PaymentOrderService orderService = new PaymentOrderService(
                orderMapper,
                qrAssetMapper,
                session.getMapper(OrderMatchAuditMapper.class),
                session.getMapper(PaymentEventMapper.class),
                qrAssetService,
                properties,
                mock(WebhookOutboxService.class),
                mock(PaymentTransactionService.class),
                amountSlotService,
                mock(SensitiveOperationLogService.class),
                lifecycle,
                mock(org.dromara.payment.context.MerchantAccessService.class),
                mock(org.dromara.payment.service.MerchantDisplayService.class)
            );

            var first = orderService.createForMerchant(
                MERCHANT_A,
                orderRequest("ORDER-A-001", "QR-A", 10_000)
            );
            var second = orderService.createForMerchant(
                MERCHANT_A,
                orderRequest("ORDER-A-002", "QR-A", 10_000)
            );
            var idempotent = orderService.createForMerchant(
                MERCHANT_A,
                orderRequest("ORDER-A-001", "QR-A", 10_000)
            );
            var otherMerchant = orderService.createForMerchant(
                MERCHANT_B,
                orderRequest("ORDER-A-001", "QR-B", 10_000)
            );

            assertEquals(10_000L, first.payableAmountMinor());
            assertEquals(10_001L, second.payableAmountMinor());
            assertEquals(first.payableAmountMinor(), idempotent.payableAmountMinor());
            assertEquals(10_000L, otherMerchant.payableAmountMinor());
            assertEquals("ORDER-A-001", otherMerchant.merchantOrderNo());
            assertThrows(
                MerchantApiException.class,
                () -> orderService.createForMerchant(
                    MERCHANT_A,
                    orderRequest("ORDER-A-003", "QR-B", 10_000)
                )
            );
            assertEquals(3, orderMapper.selectCount(new LambdaQueryWrapper<>()));
            assertEquals(3, amountSlotMapper.selectCount(new LambdaQueryWrapper<>()));
            assertEquals(2, orderMapper.selectCount(
                new LambdaQueryWrapper<PmPaymentOrder>()
                    .eq(PmPaymentOrder::getMerchantId, MERCHANT_A)
            ));

            PmPaymentOrder firstOrder = orderMapper.selectOne(
                new LambdaQueryWrapper<PmPaymentOrder>()
                    .eq(PmPaymentOrder::getMerchantId, MERCHANT_A)
                    .eq(PmPaymentOrder::getMerchantOrderNo, "ORDER-A-001")
                    .last("limit 1")
            );
            WebhookEndpointService endpointService = new WebhookEndpointService(
                endpointMapper,
                outboxMapper,
                mock(DeviceSecretCipher.class),
                mock(WebhookUrlValidator.class),
                mock(org.dromara.payment.context.MerchantAccessService.class),
                mock(org.dromara.payment.service.MerchantDisplayService.class)
            );
            WebhookOutboxService outboxService = new WebhookOutboxService(
                outboxMapper,
                session.getMapper(WebhookDeliveryLogMapper.class),
                endpointService,
                JsonMapper.builder().build(),
                mock(org.dromara.payment.service.MerchantDisplayService.class)
            );

            outboxService.enqueueOrderEvent(
                firstOrder,
                null,
                "payment.order.confirmed"
            );
            outboxService.enqueueOrderEvent(
                firstOrder,
                null,
                "payment.order.confirmed"
            );
            assertEquals(1, outboxMapper.selectCount(new LambdaQueryWrapper<>()));

            OffsetDateTime claimAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1);
            var firstClaim = outboxMapper.claimDue(
                claimAt,
                claimAt.minusSeconds(120),
                10
            );
            assertEquals(1, firstClaim.size());
            var outbox = firstClaim.getFirst();
            assertEquals("DELIVERING", outbox.getStatus());
            String deliveryId = outbox.getDeliveryId();

            outbox.setStatus("RETRYING");
            outbox.setAttemptCount(1);
            outbox.setNextAttemptAt(claimAt.minusSeconds(1));
            outbox.setLastHttpStatus(503);
            outbox.setLastError("fixture unavailable");
            outboxMapper.updateById(outbox);

            var retryClaim = outboxMapper.claimDue(
                claimAt.plusSeconds(1),
                claimAt.minusSeconds(119),
                10
            );
            assertEquals(1, retryClaim.size());
            assertEquals(deliveryId, retryClaim.getFirst().getDeliveryId());

            outbox = retryClaim.getFirst();
            outbox.setStatus("DEAD");
            outbox.setAttemptCount(12);
            outbox.setResolutionStatus("OPEN");
            outbox.setUpdatedAt(claimAt.plusSeconds(2));
            outboxMapper.updateById(outbox);
            Long deadOutboxId = outbox.getId();

            MerchantContext.set(MERCHANT_A, false);
            try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
                login.when(LoginHelper::getUserId).thenReturn(7001L);
                WebhookResolutionRequest resolution = new WebhookResolutionRequest();
                resolution.setStatus("RESOLVED");
                resolution.setNote("fixture incident closed");
                var resolved = outboxService.resolve(deadOutboxId, resolution);
                assertEquals("DEAD", resolved.getStatus());
                assertEquals("RESOLVED", resolved.getResolutionStatus());
                assertEquals(7001L, resolved.getResolvedBy());
            } finally {
                MerchantContext.clear();
            }

            MerchantContext.set(MERCHANT_B, false);
            try {
                assertThrows(
                    ServiceException.class,
                    () -> outboxService.queryById(deadOutboxId)
                );
            } finally {
                MerchantContext.clear();
            }

            assertEquals(10_000L, firstOrder.getPayableAmountMinor());
        }
    }

    @Test
    void reusesSmallestOffsetAfterCoolingExpires() throws Exception {
        DataSource dataSource =
            PaymentPostgresTestSupport.migrateLatest(POSTGRES, "amount_slot_reuse");
        insertFixtures(dataSource);
        var sessionFactory = PaymentPostgresTestSupport.sqlSessionFactory(dataSource);

        try (SqlSession session = sessionFactory.openSession(true)) {
            PaymentOrderMapper orderMapper = session.getMapper(PaymentOrderMapper.class);
            QrAssetMapper qrAssetMapper = session.getMapper(QrAssetMapper.class);
            AmountSlotMapper amountSlotMapper = session.getMapper(AmountSlotMapper.class);
            MerchantLifecycleService lifecycle = mock(MerchantLifecycleService.class);
            when(lifecycle.requireActive(anyLong())).thenAnswer(invocation -> {
                var merchant = new org.dromara.payment.domain.PmMerchant();
                merchant.setId(invocation.getArgument(0));
                merchant.setLifecycleStatus("ACTIVE");
                return merchant;
            });
            AmountSlotService amountSlotService = new AmountSlotService(
                amountSlotMapper,
                mock(org.dromara.payment.service.MerchantDisplayService.class));
            PaymentOrderService orderService = new PaymentOrderService(
                orderMapper,
                qrAssetMapper,
                session.getMapper(OrderMatchAuditMapper.class),
                session.getMapper(PaymentEventMapper.class),
                new QrAssetService(
                    qrAssetMapper,
                    orderMapper,
                    mock(org.dromara.payment.context.MerchantAccessService.class),
                    mock(org.dromara.payment.service.MerchantDisplayService.class)),
                new PaymentProperties(),
                mock(WebhookOutboxService.class),
                mock(PaymentTransactionService.class),
                amountSlotService,
                mock(SensitiveOperationLogService.class),
                lifecycle,
                mock(org.dromara.payment.context.MerchantAccessService.class),
                mock(org.dromara.payment.service.MerchantDisplayService.class)
            );

            var first = orderService.createForMerchant(
                MERCHANT_A,
                orderRequest("ORDER-REUSE-001", "QR-A", 20_000)
            );
            assertEquals(20_000L, first.payableAmountMinor());

            orderService.cancelForMerchant(MERCHANT_A, "ORDER-REUSE-001");
            var coolingSlot = amountSlotMapper.selectOne(
                new LambdaQueryWrapper<PmAmountSlotReservation>()
                    .eq(PmAmountSlotReservation::getMerchantId, MERCHANT_A)
                    .eq(PmAmountSlotReservation::getPlatform, "WECHAT")
                    .eq(PmAmountSlotReservation::getPayableAmountMinor, 20_000L)
                    .last("limit 1")
            );
            coolingSlot.setCoolingUntil(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
            amountSlotMapper.updateById(coolingSlot);

            var reused = orderService.createForMerchant(
                MERCHANT_A,
                orderRequest("ORDER-REUSE-002", "QR-A", 20_000)
            );
            assertEquals(20_000L, reused.payableAmountMinor());

            PmPaymentOrder reusedOrder = orderMapper.selectOne(
                new LambdaQueryWrapper<PmPaymentOrder>()
                    .eq(PmPaymentOrder::getMerchantId, MERCHANT_A)
                    .eq(PmPaymentOrder::getMerchantOrderNo, "ORDER-REUSE-002")
                    .last("limit 1")
            );
            var activeSlot = amountSlotMapper.selectOne(
                new LambdaQueryWrapper<PmAmountSlotReservation>()
                    .eq(PmAmountSlotReservation::getMerchantId, MERCHANT_A)
                    .eq(PmAmountSlotReservation::getPlatform, "WECHAT")
                    .eq(PmAmountSlotReservation::getPayableAmountMinor, 20_000L)
                    .last("limit 1")
            );
            assertEquals(reusedOrder.getId(), activeSlot.getOrderId());
            assertEquals("ACTIVE", activeSlot.getStatus());
        }
    }

    @Test
    void manualMatchUsesEligibleCandidateAndCompletesPaymentLifecycle()
        throws Exception {
        DataSource dataSource =
            PaymentPostgresTestSupport.migrateLatest(POSTGRES, "manual_match");
        insertFixtures(dataSource);
        var sessionFactory = PaymentPostgresTestSupport.sqlSessionFactory(dataSource);

        try (SqlSession session = sessionFactory.openSession(true)) {
            PaymentOrderMapper orderMapper = session.getMapper(PaymentOrderMapper.class);
            PaymentEventMapper eventMapper = session.getMapper(PaymentEventMapper.class);
            PaymentTransactionMapper transactionMapper =
                session.getMapper(PaymentTransactionMapper.class);
            OrderMatchAuditMapper auditMapper =
                session.getMapper(OrderMatchAuditMapper.class);
            QrAssetMapper qrAssetMapper = session.getMapper(QrAssetMapper.class);
            AmountSlotMapper amountSlotMapper = session.getMapper(AmountSlotMapper.class);
            MerchantLifecycleService lifecycle = activeLifecycle();
            WebhookOutboxService webhook = mock(WebhookOutboxService.class);
            AmountSlotService amountSlotService = new AmountSlotService(
                amountSlotMapper,
                mock(org.dromara.payment.service.MerchantDisplayService.class));
            PaymentTransactionService transactionService =
                new PaymentTransactionService(
                    transactionMapper,
                    orderMapper,
                    webhook,
                    mock(org.dromara.payment.service.MerchantDisplayService.class));
            PaymentOrderService orderService = new PaymentOrderService(
                orderMapper,
                qrAssetMapper,
                auditMapper,
                eventMapper,
                new QrAssetService(
                    qrAssetMapper,
                    orderMapper,
                    mock(org.dromara.payment.context.MerchantAccessService.class),
                    mock(org.dromara.payment.service.MerchantDisplayService.class)),
                new PaymentProperties(),
                webhook,
                transactionService,
                amountSlotService,
                mock(SensitiveOperationLogService.class),
                lifecycle,
                mock(org.dromara.payment.context.MerchantAccessService.class),
                mock(org.dromara.payment.service.MerchantDisplayService.class)
            ) {
                @Override
                public org.dromara.payment.domain.vo.PaymentOrderVo queryById(
                    Long id
                ) {
                    PmPaymentOrder stored = orderMapper.selectById(id);
                    var result =
                        new org.dromara.payment.domain.vo.PaymentOrderVo();
                    result.setId(stored.getId());
                    result.setMerchantId(stored.getMerchantId());
                    result.setStatus(stored.getStatus());
                    result.setMatchedEventId(stored.getMatchedEventId());
                    result.setTransactionId(stored.getTransactionId());
                    return result;
                }
            };

            orderService.createForMerchant(
                MERCHANT_A,
                orderRequest("ORDER-MATCH-001", "QR-A", 30_000)
            );
            PmPaymentOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<PmPaymentOrder>()
                    .eq(PmPaymentOrder::getMerchantId, MERCHANT_A)
                    .eq(PmPaymentOrder::getMerchantOrderNo, "ORDER-MATCH-001")
                    .last("limit 1")
            );
            PmPaymentEvent exactEvent = paymentEvent(
                6151L,
                "EVENT-MATCH-001",
                "WECHAT",
                30_000L
            );
            assertEquals(1, eventMapper.insertOnConflict(exactEvent));

            MerchantContext.set(MERCHANT_A, false);
            try {
                var candidates = orderService.matchCandidates(order.getId());
                assertEquals(1, candidates.size());
                assertTrue(candidates.getFirst().isExactMatch());

                ManualOrderMatchRequest request = new ManualOrderMatchRequest();
                request.setEventId(exactEvent.getId());
                request.setForce(false);
                request.setNote("人工核对到账");
                try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
                    login.when(LoginHelper::isLogin).thenReturn(true);
                    login.when(LoginHelper::getUserId).thenReturn(7001L);
                    var matched = orderService.manualMatch(
                        order.getId(),
                        request,
                        StepUpVerificationMethod.SESSION
                    );
                    assertEquals(PaymentConstants.ORDER_STATUS_PAID, matched.getStatus());
                }
            } finally {
                MerchantContext.clear();
            }

            PmPaymentOrder matchedOrder = orderMapper.selectById(order.getId());
            PmPaymentEvent matchedEvent = eventMapper.selectById(exactEvent.getId());
            PmPaymentTransaction transaction = transactionMapper.selectOne(
                new LambdaQueryWrapper<PmPaymentTransaction>()
                    .eq(PmPaymentTransaction::getEventId, exactEvent.getId())
                    .last("limit 1")
            );
            PmOrderMatchAudit audit = auditMapper.selectOne(
                new LambdaQueryWrapper<PmOrderMatchAudit>()
                    .eq(PmOrderMatchAudit::getOrderId, order.getId())
                    .eq(PmOrderMatchAudit::getAction, "MANUAL_MATCH")
                    .last("limit 1")
            );
            PmAmountSlotReservation slot = amountSlotMapper.selectOne(
                new LambdaQueryWrapper<PmAmountSlotReservation>()
                    .eq(PmAmountSlotReservation::getOrderId, order.getId())
                    .last("limit 1")
            );

            assertEquals(PaymentConstants.ORDER_STATUS_PAID, matchedOrder.getStatus());
            assertEquals(PaymentConstants.EVENT_STATUS_MATCHED, matchedEvent.getStatus());
            assertNotNull(transaction);
            assertEquals(order.getId(), transaction.getOrderId());
            assertEquals(PaymentConstants.TRANSACTION_MATCHED, transaction.getStatus());
            assertNotNull(audit);
            assertEquals(PaymentConstants.SLOT_COOLING, slot.getStatus());
            assertNotNull(slot.getCoolingUntil());

            orderService.createForMerchant(
                MERCHANT_A,
                orderRequest("ORDER-MATCH-002", "QR-A", 40_000)
            );
            PmPaymentOrder mismatchOrder = orderMapper.selectOne(
                new LambdaQueryWrapper<PmPaymentOrder>()
                    .eq(PmPaymentOrder::getMerchantId, MERCHANT_A)
                    .eq(PmPaymentOrder::getMerchantOrderNo, "ORDER-MATCH-002")
                    .last("limit 1")
            );
            PmPaymentEvent mismatchEvent = paymentEvent(
                6152L,
                "EVENT-MATCH-002",
                "ALIPAY",
                40_000L
            );
            assertEquals(1, eventMapper.insertOnConflict(mismatchEvent));
            ManualOrderMatchRequest mismatchRequest = new ManualOrderMatchRequest();
            mismatchRequest.setEventId(mismatchEvent.getId());
            mismatchRequest.setForce(false);

            MerchantContext.set(MERCHANT_A, false);
            try {
                assertThrows(
                    ServiceException.class,
                    () -> orderService.manualMatch(
                        mismatchOrder.getId(),
                        mismatchRequest,
                        StepUpVerificationMethod.SESSION
                    )
                );
            } finally {
                MerchantContext.clear();
            }
            assertEquals(
                PaymentConstants.ORDER_STATUS_PENDING,
                orderMapper.selectById(mismatchOrder.getId()).getStatus()
            );
            assertEquals(
                PaymentConstants.EVENT_STATUS_RECEIVED,
                eventMapper.selectById(mismatchEvent.getId()).getStatus()
            );

            mismatchRequest.setForce(true);
            mismatchRequest.setNote("强制匹配数据库约束回归");
            MerchantContext.set(MERCHANT_A, false);
            try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
                login.when(LoginHelper::isLogin).thenReturn(true);
                login.when(LoginHelper::getUserId).thenReturn(7002L);
                var forced = orderService.manualMatch(
                    mismatchOrder.getId(),
                    mismatchRequest,
                    StepUpVerificationMethod.MFA
                );
                assertEquals(PaymentConstants.ORDER_STATUS_PAID, forced.getStatus());
            } finally {
                MerchantContext.clear();
            }

            PmPaymentOrder forcedOrder = orderMapper.selectById(mismatchOrder.getId());
            PmPaymentEvent forcedEvent = eventMapper.selectById(mismatchEvent.getId());
            PmPaymentTransaction forcedTransaction = transactionMapper.selectOne(
                new LambdaQueryWrapper<PmPaymentTransaction>()
                    .eq(PmPaymentTransaction::getEventId, mismatchEvent.getId())
                    .last("limit 1")
            );
            PmOrderMatchAudit forceAudit = auditMapper.selectOne(
                new LambdaQueryWrapper<PmOrderMatchAudit>()
                    .eq(PmOrderMatchAudit::getOrderId, mismatchOrder.getId())
                    .eq(PmOrderMatchAudit::getAction, "FORCE_MATCH")
                    .last("limit 1")
            );
            PmAmountSlotReservation forcedSlot = amountSlotMapper.selectOne(
                new LambdaQueryWrapper<PmAmountSlotReservation>()
                    .eq(PmAmountSlotReservation::getOrderId, mismatchOrder.getId())
                    .last("limit 1")
            );

            assertEquals(PaymentConstants.ORDER_STATUS_PAID, forcedOrder.getStatus());
            assertEquals(PaymentConstants.EVENT_STATUS_MATCHED, forcedEvent.getStatus());
            assertNotNull(forcedTransaction);
            assertEquals(mismatchOrder.getId(), forcedTransaction.getOrderId());
            assertEquals(PaymentConstants.TRANSACTION_CONFIRMED, forcedTransaction.getStatus());
            assertEquals(
                PaymentConstants.CONFIRMATION_MANUAL,
                forcedTransaction.getConfirmationStatus()
            );
            assertNotNull(forceAudit);
            assertEquals(PaymentConstants.SLOT_COOLING, forcedSlot.getStatus());
            assertNotNull(forcedSlot.getCoolingUntil());
        }
    }

    private void insertFixtures(DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("""
                insert into pm_merchant (
                    id, merchant_code, name, status, lifecycle_status,
                    timezone, quota_config, created_at, updated_at
                ) values
                    (6101, 'MERCHANT-A', 'Merchant A', '0', 'ACTIVE',
                     'Asia/Shanghai', '{}'::jsonb, now(), now()),
                    (6201, 'MERCHANT-B', 'Merchant B', '0', 'ACTIVE',
                     'Asia/Shanghai', '{}'::jsonb, now(), now())
                """);
            statement.execute("""
                insert into pm_qr_asset (
                    id, merchant_id, platform, asset_name, asset_code,
                    qr_content_template, status, created_at, updated_at
                ) values
                    (6111, 6101, 'WECHAT', 'QR A', 'QR-A',
                     'wx://pay?amount={amountMinor}&order={orderNo}', '0', now(), now()),
                    (6211, 6201, 'WECHAT', 'QR B', 'QR-B',
                     'wx://pay?amount={amountMinor}&order={orderNo}', '0', now(), now())
                """);
            statement.execute("""
                insert into pm_device (
                    id, merchant_id, device_name, status, paired_at,
                    created_at, updated_at
                ) values
                    (6112, 6101, 'Device A', '0', now(), now(), now()),
                    (6212, 6201, 'Device B', '0', now(), now(), now())
                """);
            statement.execute("""
                insert into pm_webhook_endpoint (
                    id, merchant_id, endpoint_name, endpoint_url,
                    secret_ciphertext, status, event_types, platform_filter,
                    created_at, updated_at
                ) values (
                    6131, 6101, 'Confirmed endpoint',
                    'https://merchant.example.test/webhook',
                    'fixture-secret-ciphertext', '0',
                    'payment.order.confirmed,payment.order.reconciled',
                    'ALL', now(), now()
                )
                """);
        }
    }

    private MerchantOrderCreateRequest orderRequest(
        String orderNo,
        String qrAssetCode,
        long amountMinor
    ) {
        MerchantOrderCreateRequest request = new MerchantOrderCreateRequest();
        request.setMerchantOrderNo(orderNo);
        request.setPlatform("WECHAT");
        request.setQrAssetCode(qrAssetCode);
        request.setAmountMinor(amountMinor);
        request.setExpiresSeconds(300);
        request.setSubject("Fixture order");
        return request;
    }

    private MerchantLifecycleService activeLifecycle() {
        MerchantLifecycleService lifecycle = mock(MerchantLifecycleService.class);
        when(lifecycle.requireActive(anyLong())).thenAnswer(invocation -> {
            var merchant = new org.dromara.payment.domain.PmMerchant();
            merchant.setId(invocation.getArgument(0));
            merchant.setLifecycleStatus("ACTIVE");
            return merchant;
        });
        return lifecycle;
    }

    private PmPaymentEvent paymentEvent(
        Long id,
        String clientEventId,
        String platform,
        Long amountMinor
    ) {
        OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);
        PmPaymentEvent event = new PmPaymentEvent();
        event.setId(id);
        event.setMerchantId(MERCHANT_A);
        event.setDeviceId(6112L);
        event.setClientEventId(clientEventId);
        event.setPlatform(platform);
        event.setDirection("INCOME");
        event.setAmountMinor(amountMinor);
        event.setCurrency("CNY");
        event.setEventTime(timestamp);
        event.setEventTimeMs(timestamp.toInstant().toEpochMilli());
        event.setClientReceivedAt(timestamp);
        event.setClientReceivedAtMs(timestamp.toInstant().toEpochMilli());
        event.setClientSentAt(timestamp);
        event.setClientSentAtMs(timestamp.toInstant().toEpochMilli());
        event.setReceivedAt(timestamp);
        event.setParseStatus("PARSED");
        event.setParserVersion("fixture");
        event.setFingerprint("fingerprint-" + id);
        event.setRawPayload("{}");
        event.setStatus(PaymentConstants.EVENT_STATUS_RECEIVED);
        event.setDuplicateStatus(PaymentConstants.DUPLICATE_STATUS_NONE);
        return event;
    }
}
