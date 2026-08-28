package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.PmPaymentTransaction;
import org.dromara.payment.domain.PmWebhookDeliveryLog;
import org.dromara.payment.domain.PmWebhookEndpoint;
import org.dromara.payment.domain.PmWebhookOutbox;
import org.dromara.payment.domain.bo.WebhookOutboxQueryBo;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.vo.WebhookOutboxVo;
import org.dromara.payment.context.MerchantContext;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.domain.dto.WebhookResolutionRequest;
import org.dromara.payment.mapper.WebhookDeliveryLogMapper;
import org.dromara.payment.mapper.WebhookOutboxMapper;
import org.dromara.payment.integration.epay.callback.ProtocolCallbackOutboxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebhookOutboxService {

    private final WebhookOutboxMapper outboxMapper;
    private final WebhookDeliveryLogMapper logMapper;
    private final WebhookEndpointService endpointService;
    private final ObjectMapper objectMapper;
    private final MerchantDisplayService merchantDisplayService;
    private ProtocolCallbackOutboxService protocolCallbackOutboxService;

    @Autowired(required = false)
    public void setProtocolCallbackOutboxService(
        ProtocolCallbackOutboxService protocolCallbackOutboxService
    ) {
        this.protocolCallbackOutboxService = protocolCallbackOutboxService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void enqueueOrderPaid(PmPaymentOrder order, PmPaymentEvent event) {
        enqueueOrderEvent(order, event, "payment.order.paid");
    }

    @Transactional(rollbackFor = Exception.class)
    public void enqueueOrderEvent(
        PmPaymentOrder order,
        PmPaymentEvent event,
        String eventType
    ) {
        List<PmWebhookEndpoint> endpoints = endpointService.enabledEndpoints(
            order.getMerchantId(),
            eventType,
            order.getPlatform());
        OffsetDateTime now = now();
        String eventId = businessEventId(
            order.getMerchantId(), eventType, "PAYMENT_ORDER", order.getId());
        for (PmWebhookEndpoint endpoint : endpoints) {
            PmWebhookOutbox outbox = new PmWebhookOutbox();
            outbox.setId(IdWorker.getId());
            outbox.setDeliveryId(UUID.randomUUID().toString());
            outbox.setEventId(eventId);
            outbox.setSchemaVersion(PaymentConstants.WEBHOOK_SCHEMA_VERSION);
            outbox.setMerchantId(order.getMerchantId());
            outbox.setEndpointId(endpoint.getId());
            outbox.setAggregateType("PAYMENT_ORDER");
            outbox.setAggregateId(order.getId());
            outbox.setEventType(eventType);
            outbox.setStatus("PENDING");
            outbox.setAttemptCount(0);
            outbox.setNextAttemptAt(now);
            outbox.setCreatedAt(now);
            outbox.setUpdatedAt(now);
            outbox.setPayload(buildOrderPayload(
                outbox.getDeliveryId(),
                eventId,
                eventType,
                order,
                event,
                now));
            outboxMapper.insertOnConflict(outbox);
        }
        if (protocolCallbackOutboxService != null) {
            protocolCallbackOutboxService.onOrderEvent(order, eventType);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public WebhookOutboxVo testEndpoint(Long endpointId) {
        PmWebhookEndpoint endpoint = endpointService.requireAccessible(endpointId);
        OffsetDateTime timestamp = now();
        PmWebhookOutbox outbox = new PmWebhookOutbox();
        outbox.setId(IdWorker.getId());
        outbox.setDeliveryId(UUID.randomUUID().toString());
        outbox.setEventId(UUID.randomUUID().toString());
        outbox.setSchemaVersion(PaymentConstants.WEBHOOK_SCHEMA_VERSION);
        outbox.setMerchantId(endpoint.getMerchantId());
        outbox.setEndpointId(endpoint.getId());
        outbox.setAggregateType("WEBHOOK_ENDPOINT");
        outbox.setAggregateId(IdWorker.getId());
        outbox.setEventType("payment.webhook.test");
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(timestamp);
        outbox.setCreatedAt(timestamp);
        outbox.setUpdatedAt(timestamp);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", PaymentConstants.WEBHOOK_SCHEMA_VERSION);
        payload.put("deliveryId", outbox.getDeliveryId());
        payload.put("eventId", outbox.getEventId());
        payload.put("type", outbox.getEventType());
        payload.put("createdAt", timestamp);
        payload.put("merchantId", endpoint.getMerchantId());
        payload.put("message", "Payment Monitor webhook test");
        outbox.setPayload(writePayload(payload));
        outboxMapper.insertOnConflict(outbox);
        return toVo(outbox, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public WebhookOutboxVo replay(Long id, String reason) {
        PmWebhookOutbox original = requireAdminOutbox(id);
        OffsetDateTime timestamp = now();
        String deliveryId = UUID.randomUUID().toString();
        PmWebhookOutbox replay = new PmWebhookOutbox();
        replay.setId(IdWorker.getId());
        replay.setDeliveryId(deliveryId);
        replay.setEventId(original.getEventId());
        replay.setSchemaVersion(PaymentConstants.WEBHOOK_SCHEMA_VERSION);
        replay.setMerchantId(original.getMerchantId());
        replay.setEndpointId(original.getEndpointId());
        replay.setAggregateType(original.getAggregateType());
        replay.setAggregateId(original.getAggregateId());
        replay.setEventType(original.getEventType());
        replay.setPayload(rebuildReplayPayload(
            original.getPayload(),
            deliveryId,
            original.getEventId()));
        replay.setStatus("PENDING");
        replay.setAttemptCount(0);
        replay.setNextAttemptAt(timestamp);
        replay.setReplayOfDeliveryId(original.getDeliveryId());
        replay.setReplayReason(StringUtils.isBlank(reason) ? "管理端人工重放" : reason.trim());
        replay.setCreatedAt(timestamp);
        replay.setUpdatedAt(timestamp);
        outboxMapper.insertOnConflict(replay);
        return toVo(replay, false);
    }

    public PageResult<WebhookOutboxVo> queryPage(
        WebhookOutboxQueryBo bo,
        PageQuery pageQuery
    ) {
        LambdaQueryWrapper<PmWebhookOutbox> wrapper = buildQuery(bo)
            .orderByDesc(PmWebhookOutbox::getCreatedAt);
        Page<PmWebhookOutbox> page = outboxMapper.selectPage(pageQuery.build(), wrapper);
        List<WebhookOutboxVo> rows = page.getRecords().stream().map(item -> toVo(item, false)).toList();
        merchantDisplayService.enrich(
            rows,
            WebhookOutboxVo::getMerchantId,
            WebhookOutboxVo::setMerchantCode,
            WebhookOutboxVo::setMerchantName);
        return PageResult.build(rows, page.getTotal());
    }

    public WebhookOutboxVo queryById(Long id) {
        PmWebhookOutbox outbox = requireAdminOutbox(id);
        WebhookOutboxVo vo = toVo(outbox, true);
        merchantDisplayService.enrich(
            List.of(vo),
            WebhookOutboxVo::getMerchantId,
            WebhookOutboxVo::setMerchantCode,
            WebhookOutboxVo::setMerchantName);
        return vo;
    }

    public WebhookOutboxVo retry(Long id) {
        PmWebhookOutbox outbox = requireAdminOutbox(id);
        if ("DELIVERING".equals(outbox.getStatus())) {
            throw new ServiceException("Webhook 正在投递中");
        }
        OffsetDateTime now = now();
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(now);
        outbox.setLockedAt(null);
        outbox.setDeliveredAt(null);
        outbox.setLastHttpStatus(null);
        outbox.setLastError(null);
        outbox.setResolutionStatus("OPEN");
        outbox.setResolvedBy(null);
        outbox.setResolvedAt(null);
        outbox.setResolutionNote(null);
        outbox.setUpdatedAt(now);
        outboxMapper.updateById(outbox);
        return queryById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void retry(List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<PmWebhookOutbox> outboxes = outboxMapper.selectList(
            new LambdaQueryWrapper<PmWebhookOutbox>().in(PmWebhookOutbox::getId, distinctIds));
        if (outboxes.size() != distinctIds.size()) {
            throw new ServiceException("部分 Webhook 投递任务不存在");
        }
        MerchantContext.requireSingleAccessibleMerchant(
            outboxes.stream().map(PmWebhookOutbox::getMerchantId).toList());
        for (Long id : distinctIds) {
            retry(id);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public WebhookOutboxVo resolve(Long id, WebhookResolutionRequest request) {
        PmWebhookOutbox outbox = requireAdminOutbox(id);
        if (!"DEAD".equals(outbox.getStatus())) {
            throw new ServiceException("只有 DEAD Webhook 可以完成人工处理");
        }
        outbox.setResolutionStatus(request.getStatus());
        outbox.setResolutionNote(request.getNote().trim());
        outbox.setResolvedBy(LoginHelper.getUserId());
        outbox.setResolvedAt(now());
        outbox.setUpdatedAt(now());
        outboxMapper.updateById(outbox);
        return queryById(id);
    }

    private String buildOrderPayload(
        String deliveryId,
        String eventId,
        String eventType,
        PmPaymentOrder order,
        PmPaymentEvent event,
        OffsetDateTime createdAt
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put("merchantOrderNo", order.getMerchantOrderNo());
        data.put("platform", order.getPlatform());
        data.put("requestedAmountMinor", order.getRequestedAmountMinor());
        data.put("payableAmountMinor", order.getPayableAmountMinor());
        data.put("currency", order.getCurrency());
        data.put("status", order.getStatus());
        data.put("transactionId", order.getTransactionId());
        data.put("confirmationStatus", order.getConfirmationStatus());
        data.put("confirmedAt", order.getConfirmedAt());
        data.put("confirmationSource", order.getConfirmationSource());
        data.put("paidAt", order.getPaidAt());
        data.put("cancelledAt", order.getCancelledAt());
        data.put("expiresAt", order.getExpiresAt());
        if (event != null) {
            data.put("eventId", event.getId());
            data.put("clientEventId", event.getClientEventId());
            data.put("eventTime", event.getEventTime());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", PaymentConstants.WEBHOOK_SCHEMA_VERSION);
        payload.put("deliveryId", deliveryId);
        payload.put("eventId", eventId);
        payload.put("type", eventType);
        payload.put("createdAt", createdAt);
        payload.put("data", data);
        return writePayload(payload);
    }

    @Transactional(rollbackFor = Exception.class)
    public void enqueueTransactionObserved(
        PmPaymentTransaction transaction,
        PmPaymentEvent event
    ) {
        String eventType = "payment.transaction.observed";
        List<PmWebhookEndpoint> endpoints = endpointService.enabledEndpoints(
            transaction.getMerchantId(), eventType, transaction.getPlatform());
        OffsetDateTime timestamp = now();
        String eventId = businessEventId(
            transaction.getMerchantId(), eventType, "PAYMENT_TRANSACTION", transaction.getId());
        for (PmWebhookEndpoint endpoint : endpoints) {
            String deliveryId = UUID.randomUUID().toString();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("transactionId", transaction.getId());
            data.put("eventId", transaction.getEventId());
            data.put("clientEventId", event.getClientEventId());
            data.put("platform", transaction.getPlatform());
            data.put("amountMinor", transaction.getAmountMinor());
            data.put("currency", transaction.getCurrency());
            data.put("status", transaction.getStatus());
            data.put("confirmationStatus", transaction.getConfirmationStatus());
            data.put("observedAt", transaction.getObservedAt());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schemaVersion", PaymentConstants.WEBHOOK_SCHEMA_VERSION);
            payload.put("deliveryId", deliveryId);
            payload.put("eventId", eventId);
            payload.put("type", eventType);
            payload.put("createdAt", timestamp);
            payload.put("data", data);

            PmWebhookOutbox outbox = new PmWebhookOutbox();
            outbox.setId(IdWorker.getId());
            outbox.setDeliveryId(deliveryId);
            outbox.setEventId(eventId);
            outbox.setSchemaVersion(PaymentConstants.WEBHOOK_SCHEMA_VERSION);
            outbox.setMerchantId(transaction.getMerchantId());
            outbox.setEndpointId(endpoint.getId());
            outbox.setAggregateType("PAYMENT_TRANSACTION");
            outbox.setAggregateId(transaction.getId());
            outbox.setEventType(eventType);
            outbox.setPayload(writePayload(payload));
            outbox.setStatus("PENDING");
            outbox.setAttemptCount(0);
            outbox.setNextAttemptAt(timestamp);
            outbox.setCreatedAt(timestamp);
            outbox.setUpdatedAt(timestamp);
            outboxMapper.insertOnConflict(outbox);
        }
    }

    @SuppressWarnings("unchecked")
    private String rebuildReplayPayload(
        String payload,
        String deliveryId,
        String eventId
    ) {
        try {
            Map<String, Object> values = objectMapper.readValue(payload, Map.class);
            values.put("schemaVersion", PaymentConstants.WEBHOOK_SCHEMA_VERSION);
            values.put("deliveryId", deliveryId);
            values.put("eventId", eventId);
            values.put("replayedAt", now());
            return objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            throw new ServiceException("重建 Webhook 负载失败");
        }
    }

    private String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new ServiceException("生成 Webhook 负载失败");
        }
    }

    private LambdaQueryWrapper<PmWebhookOutbox> buildQuery(WebhookOutboxQueryBo bo) {
        Long merchantId = MerchantContext.resolveQueryMerchantId(bo.getMerchantId());
        return new LambdaQueryWrapper<PmWebhookOutbox>()
            .eq(merchantId != null, PmWebhookOutbox::getMerchantId, merchantId)
            .eq(StringUtils.isNotBlank(bo.getDeliveryId()), PmWebhookOutbox::getDeliveryId, bo.getDeliveryId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), PmWebhookOutbox::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getEventType()), PmWebhookOutbox::getEventType, bo.getEventType())
            .eq(bo.getAggregateId() != null, PmWebhookOutbox::getAggregateId, bo.getAggregateId())
            .eq(bo.getEndpointId() != null, PmWebhookOutbox::getEndpointId, bo.getEndpointId())
            .eq(StringUtils.isNotBlank(bo.getResolutionStatus()),
                PmWebhookOutbox::getResolutionStatus, bo.getResolutionStatus());
    }

    private WebhookOutboxVo toVo(PmWebhookOutbox outbox, boolean includeLogs) {
        WebhookOutboxVo vo = new WebhookOutboxVo();
        vo.setId(outbox.getId());
        vo.setMerchantId(outbox.getMerchantId());
        vo.setDeliveryId(outbox.getDeliveryId());
        vo.setEventId(outbox.getEventId());
        vo.setEndpointId(outbox.getEndpointId());
        PmWebhookEndpoint endpoint = endpointService.requireForMerchant(
            outbox.getEndpointId(),
            outbox.getMerchantId());
        vo.setEndpointName(endpoint.getEndpointName());
        vo.setEndpointUrl(endpoint.getEndpointUrl());
        vo.setAggregateType(outbox.getAggregateType());
        vo.setAggregateId(outbox.getAggregateId());
        vo.setEventType(outbox.getEventType());
        vo.setStatus(outbox.getStatus());
        vo.setAttemptCount(outbox.getAttemptCount());
        vo.setNextAttemptAt(outbox.getNextAttemptAt());
        vo.setLockedAt(outbox.getLockedAt());
        vo.setDeliveredAt(outbox.getDeliveredAt());
        vo.setLastHttpStatus(outbox.getLastHttpStatus());
        vo.setLastError(outbox.getLastError());
        vo.setReplayOfDeliveryId(outbox.getReplayOfDeliveryId());
        vo.setReplayReason(outbox.getReplayReason());
        vo.setResolutionStatus(outbox.getResolutionStatus());
        vo.setResolvedBy(outbox.getResolvedBy());
        vo.setResolvedAt(outbox.getResolvedAt());
        vo.setResolutionNote(outbox.getResolutionNote());
        vo.setCreatedAt(outbox.getCreatedAt());
        vo.setUpdatedAt(outbox.getUpdatedAt());
        if (includeLogs) {
            vo.setDeliveryLogs(logMapper.selectVoList(
                new LambdaQueryWrapper<PmWebhookDeliveryLog>()
                    .eq(PmWebhookDeliveryLog::getOutboxId, outbox.getId())
                    .orderByDesc(PmWebhookDeliveryLog::getAttemptNumber)));
        }
        return vo;
    }

    private PmWebhookOutbox requireAdminOutbox(Long id) {
        PmWebhookOutbox outbox = outboxMapper.selectById(id);
        if (outbox == null) {
            throw new ServiceException("Webhook 投递任务不存在");
        }
        MerchantContext.requireAccessibleMerchant(outbox.getMerchantId());
        return outbox;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String businessEventId(
        Long merchantId,
        String eventType,
        String aggregateType,
        Long aggregateId
    ) {
        String value = merchantId + ":" + eventType + ":" + aggregateType + ":" + aggregateId;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
