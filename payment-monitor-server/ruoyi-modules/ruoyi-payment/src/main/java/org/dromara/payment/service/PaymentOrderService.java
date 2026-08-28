package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.api.MerchantApiException;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmOrderMatchAudit;
import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.PmPaymentTransaction;
import org.dromara.payment.domain.PmQrAsset;
import org.dromara.payment.domain.bo.PaymentOrderQueryBo;
import org.dromara.payment.domain.dto.ManualOrderMatchRequest;
import org.dromara.payment.domain.dto.MerchantOrderCreateRequest;
import org.dromara.payment.domain.dto.PaymentOrderCreateRequest;
import org.dromara.payment.domain.dto.PaymentIntegrationOrderCreateRequest;
import org.dromara.payment.domain.vo.MerchantOrderVo;
import org.dromara.payment.domain.vo.OrderMatchCandidateVo;
import org.dromara.payment.domain.vo.PaymentOrderVo;
import org.dromara.payment.domain.vo.PaymentIntegrationOrderVo;
import org.dromara.payment.domain.vo.PublicPaymentOrderVo;
import org.dromara.payment.mapper.OrderMatchAuditMapper;
import org.dromara.payment.mapper.PaymentEventMapper;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.mapper.QrAssetMapper;
import org.dromara.payment.security.PaymentCrypto;
import org.dromara.payment.security.StepUpVerificationMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrderService {
    private static final int MATCH_CANDIDATE_LIMIT = 200;
    private static final Set<String> MANUAL_MATCH_EVENT_STATUSES = Set.of(
        PaymentConstants.EVENT_STATUS_RECEIVED,
        PaymentConstants.EVENT_STATUS_REVIEWED,
        PaymentConstants.EVENT_STATUS_CONFLICT
    );

    private final PaymentOrderMapper orderMapper;
    private final QrAssetMapper qrAssetMapper;
    private final OrderMatchAuditMapper auditMapper;
    private final PaymentEventMapper eventMapper;
    private final QrAssetService qrAssetService;
    private final PaymentProperties properties;
    private final WebhookOutboxService webhookOutboxService;
    private final PaymentTransactionService transactionService;
    private final AmountSlotService amountSlotService;
    private final SensitiveOperationLogService sensitiveOperationLogService;
    private final MerchantLifecycleService lifecycleService;
    private final MerchantAccessService merchantAccessService;
    private final MerchantDisplayService merchantDisplayService;

    @Transactional(rollbackFor = Exception.class)
    public PaymentOrderVo create(PaymentOrderCreateRequest request) {
        Long merchantId = merchantAccessService.requireTargetMerchant(request.getMerchantId(), true);
        lifecycleService.requireActive(merchantId);
        PmQrAsset asset = qrAssetService.requireEnabled(
            merchantId,
            request.getQrAssetId(),
            request.getPlatform());
        String orderNo = StringUtils.isBlank(request.getMerchantOrderNo())
            ? generateOrderNo()
            : request.getMerchantOrderNo().trim();
        PmPaymentOrder order = createInternal(
            merchantId,
            orderNo,
            request.getPlatform(),
            asset,
            request.getAmountMinor(),
            request.getExpiresSeconds(),
            request.getSubject(),
            request.getCustomerNote(),
            false);
        return enrich(toVo(order), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentIntegrationOrderVo createForIntegration(PaymentIntegrationOrderCreateRequest request) {
        lifecycleService.requireActive(request.merchantId());
        PmQrAsset asset = qrAssetService.requireEnabled(
            request.merchantId(), request.qrAssetId(), request.platform());
        PmPaymentOrder order = createInternal(
            request.merchantId(), request.merchantOrderNo(), request.platform(), asset,
            request.amountMinor(), request.expiresSeconds(), request.subject(),
            request.customerNote(), false);
        return new PaymentIntegrationOrderVo(
            order.getId(), order.getMerchantId(), order.getMerchantOrderNo(),
            order.getPlatform(), order.getQrAssetId(), order.getRequestedAmountMinor(),
            order.getPayableAmountMinor(), order.getStatus(), order.getPublicToken(),
            renderQrContent(order, asset), order.getExpiresAt(), order.getPaidAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public MerchantOrderVo createForMerchant(Long merchantId, MerchantOrderCreateRequest request) {
        lifecycleService.requireActive(merchantId);
        PmQrAsset asset = qrAssetService.requireEnabledByCode(
            merchantId,
            request.getQrAssetCode(),
            request.getPlatform());
        PmPaymentOrder order = createInternal(
            merchantId,
            request.getMerchantOrderNo().trim(),
            request.getPlatform(),
            asset,
            request.getAmountMinor(),
            request.getExpiresSeconds(),
            request.getSubject(),
            request.getCustomerNote(),
            true);
        return toMerchantVo(order, asset);
    }

    public PageResult<PaymentOrderVo> queryPage(PaymentOrderQueryBo bo, PageQuery pageQuery) {
        expirePendingOrders();
        LambdaQueryWrapper<PmPaymentOrder> wrapper = buildQuery(bo)
            .orderByDesc(PmPaymentOrder::getCreatedAt);
        Page<PaymentOrderVo> page = orderMapper.selectVoPage(pageQuery.build(), wrapper);
        page.setRecords(page.getRecords().stream().map(item -> enrich(item, false)).toList());
        merchantDisplayService.enrich(
            page.getRecords(),
            PaymentOrderVo::getMerchantId,
            PaymentOrderVo::setMerchantCode,
            PaymentOrderVo::setMerchantName);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    public PaymentOrderVo queryById(Long id) {
        expirePendingOrders();
        PaymentOrderVo order = orderMapper.selectVoOne(new LambdaQueryWrapper<PmPaymentOrder>()
            .eq(PmPaymentOrder::getId, id)
            .last("limit 1"));
        if (order == null) {
            throw new ServiceException("支付订单不存在");
        }
        MerchantContext.requireAccessibleMerchant(order.getMerchantId());
        PaymentOrderVo result = enrich(order, true);
        merchantDisplayService.enrich(
            List.of(result),
            PaymentOrderVo::getMerchantId,
            PaymentOrderVo::setMerchantCode,
            PaymentOrderVo::setMerchantName);
        return result;
    }

    public List<OrderMatchCandidateVo> matchCandidates(Long orderId) {
        PmPaymentOrder order = orderMapper.selectOne(
            new LambdaQueryWrapper<PmPaymentOrder>()
                .eq(PmPaymentOrder::getId, orderId)
                .last("limit 1")
        );
        if (order == null) {
            throw new ServiceException("支付订单不存在");
        }
        MerchantContext.requireAccessibleMerchant(order.getMerchantId());
        Long merchantId = order.getMerchantId();
        return eventMapper.selectManualMatchCandidates(
                merchantId,
                orderId,
                order.getPlatform(),
                order.getPayableAmountMinor(),
                MATCH_CANDIDATE_LIMIT
            )
            .stream()
            .filter(this::isManualMatchEventEligible)
            .map(event -> toMatchCandidate(order, event))
            .sorted(
                Comparator.comparing(OrderMatchCandidateVo::isExactMatch).reversed()
                    .thenComparing(
                        candidate -> candidate.getEventTime() == null
                            ? candidate.getReceivedAt()
                            : candidate.getEventTime(),
                        Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(
                        OrderMatchCandidateVo::getId,
                        Comparator.nullsLast(Comparator.reverseOrder())
                    )
            )
            .limit(MATCH_CANDIDATE_LIMIT)
            .toList();
    }

    public MerchantOrderVo queryForMerchant(Long merchantId, String merchantOrderNo) {
        expirePendingOrders();
        PmPaymentOrder order = findEntityByMerchantOrderNo(merchantId, merchantOrderNo);
        if (order == null) {
            throw new MerchantApiException(404, "ORDER_NOT_FOUND", "订单不存在", false);
        }
        PmQrAsset asset = qrAssetMapper.selectOne(new LambdaQueryWrapper<PmQrAsset>()
            .eq(PmQrAsset::getId, order.getQrAssetId())
            .eq(PmQrAsset::getMerchantId, merchantId)
            .last("limit 1"));
        return toMerchantVo(order, asset);
    }

    public PublicPaymentOrderVo queryPublic(String token) {
        expirePendingOrders();
        PmPaymentOrder order = requireByToken(token);
        return PublicPaymentOrderVo.builder()
            .merchantOrderNo(order.getMerchantOrderNo())
            .platform(order.getPlatform())
            .payableAmountMinor(order.getPayableAmountMinor())
            .currency(order.getCurrency())
            .status(order.getStatus())
            .subject(order.getSubject())
            .createdAt(order.getCreatedAt())
            .expiresAt(order.getExpiresAt())
            .paidAt(order.getPaidAt())
            .transactionId(order.getTransactionId())
            .confirmationStatus(order.getConfirmationStatus())
            .confirmedAt(order.getConfirmedAt())
            .confirmationSource(order.getConfirmationSource())
            .qrImageUrl("/api/public/payment-orders/" + token + "/qr.svg")
            .build();
    }

    public String qrContent(String token) {
        PmPaymentOrder order = requireByToken(token);
        PmQrAsset asset = qrAssetMapper.selectOne(new LambdaQueryWrapper<PmQrAsset>()
            .eq(PmQrAsset::getId, order.getQrAssetId())
            .eq(PmQrAsset::getMerchantId, order.getMerchantId())
            .last("limit 1"));
        if (asset == null) {
            throw new ServiceException("订单收款二维码不存在");
        }
        return renderQrContent(order, asset);
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentOrderVo cancel(Long id, String note) {
        PmPaymentOrder existing = requireAdminOrder(id);
        PmPaymentOrder order = orderMapper.selectByIdForUpdate(id, existing.getMerchantId());
        if (order == null) {
            throw new ServiceException("支付订单不存在");
        }
        cancelInternal(order, note, currentUserId(), false);
        return queryById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(List<Long> ids, String note) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<PmPaymentOrder> selected = orderMapper.selectList(
            new LambdaQueryWrapper<PmPaymentOrder>().in(PmPaymentOrder::getId, distinctIds));
        if (selected.size() != distinctIds.size()) {
            throw new ServiceException("部分支付订单不存在");
        }
        Long merchantId = MerchantContext.requireSingleAccessibleMerchant(
            selected.stream().map(PmPaymentOrder::getMerchantId).toList());
        Long operatorId = currentUserId();
        for (Long id : distinctIds) {
            PmPaymentOrder order = orderMapper.selectByIdForUpdate(id, merchantId);
            if (order == null) {
                throw new ServiceException("部分支付订单不存在或不属于当前商户");
            }
            cancelInternal(order, note, operatorId, false);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public MerchantOrderVo cancelForMerchant(Long merchantId, String merchantOrderNo) {
        PmPaymentOrder existing = findEntityByMerchantOrderNo(merchantId, merchantOrderNo);
        if (existing == null) {
            throw new MerchantApiException(404, "ORDER_NOT_FOUND", "订单不存在", false);
        }
        PmPaymentOrder order = orderMapper.selectByIdForUpdate(existing.getId(), merchantId);
        cancelInternal(order, "商户 API 取消订单", null, true);
        PmQrAsset asset = qrAssetMapper.selectOne(new LambdaQueryWrapper<PmQrAsset>()
            .eq(PmQrAsset::getId, order.getQrAssetId())
            .eq(PmQrAsset::getMerchantId, merchantId)
            .last("limit 1"));
        return toMerchantVo(order, asset);
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentOrderVo manualMatch(
        Long orderId,
        ManualOrderMatchRequest request,
        StepUpVerificationMethod verificationMethod
    ) {
        PmPaymentOrder selectedOrder = requireAdminOrder(orderId);
        Long merchantId = selectedOrder.getMerchantId();
        PmPaymentOrder order = orderMapper.selectByIdForUpdate(orderId, merchantId);
        PmPaymentEvent event = eventMapper.selectByIdForUpdate(request.getEventId(), merchantId);
        if (order == null || event == null) {
            throw new ServiceException("订单或支付事件不存在");
        }
        if (PaymentConstants.ORDER_STATUS_PAID.equals(order.getStatus())) {
            if (event.getId().equals(order.getMatchedEventId())) {
                return queryById(orderId);
            }
            throw new ServiceException("订单已匹配其他支付事件");
        }
        requireManualMatchEventEligible(order, event);
        boolean exact = order.getPlatform().equals(event.getPlatform())
            && order.getPayableAmountMinor().equals(event.getAmountMinor());
        if (!exact && !request.isForce()) {
            throw new ServiceException("平台或金额不一致，确认人工补单时需启用强制匹配");
        }
        if (request.isForce()) {
            String beforeSnapshot = sensitiveOperationLogService.snapshot(
                Map.of("order", order, "event", event));
            Long operatorId = currentUserId();
            PmPaymentTransaction transaction = match(
                order,
                event,
                "FORCE_MATCH",
                request.getNote(),
                operatorId);
            transactionService.markConfirmedBySensitiveOperation(
                transaction,
                order,
                operatorId,
                request.getNote(),
                verificationMethod);
            sensitiveOperationLogService.record(
                merchantId,
                "FORCE_MATCH",
                "PAYMENT_ORDER",
                orderId,
                request.getNote(),
                Map.of(
                    "orderId", orderId,
                    "eventId", event.getId(),
                    "force", true),
                beforeSnapshot,
                Map.of(
                    "order", order,
                    "event", event,
                    "transaction", transaction),
                verificationMethod,
                "FORCE_MATCH:" + orderId + ":" + event.getId());
            return queryById(orderId);
        }
        match(order, event, "MANUAL_MATCH", request.getNote(), currentUserId());
        return queryById(orderId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void autoMatch(PmPaymentEvent incoming) {
        if (!"INCOME".equals(incoming.getDirection()) || incoming.getAmountMinor() == null) {
            return;
        }
        OffsetDateTime eventTime = incoming.getEventTime() == null
            ? incoming.getClientReceivedAt()
            : incoming.getEventTime();
        if (eventTime == null) {
            eventTime = incoming.getReceivedAt();
        }
        List<PmPaymentOrder> candidates = orderMapper.selectMatchCandidatesForUpdate(
            incoming.getMerchantId(),
            incoming.getPlatform(),
            incoming.getAmountMinor(),
            eventTime);
        if (candidates.isEmpty()) {
            return;
        }
        PmPaymentEvent event = eventMapper.selectByIdForUpdate(
            incoming.getId(),
            incoming.getMerchantId());
        if (event == null || PaymentConstants.EVENT_STATUS_MATCHED.equals(event.getStatus())) {
            return;
        }
        if (candidates.size() == 1) {
            match(candidates.getFirst(), event, "AUTO_MATCH", "平台、金额和有效时间窗口自动匹配", null);
            return;
        }
        event.setStatus(PaymentConstants.EVENT_STATUS_CONFLICT);
        eventMapper.updateById(event);
        for (PmPaymentOrder order : candidates) {
            String before = order.getStatus();
            order.setStatus(PaymentConstants.ORDER_STATUS_CONFLICT);
            order.setUpdatedAt(now());
            order.setVersion(nextVersion(order.getVersion()));
            orderMapper.updateById(order);
            audit(order, event.getId(), "CONFLICT", before, order.getStatus(), "同一事件命中多个候选订单", null);
            webhookOutboxService.enqueueOrderEvent(
                order,
                event,
                "payment.order.conflict");
        }
    }

    @Scheduled(fixedDelayString = "${payment.orders.expire-scan-ms:30000}")
    @Transactional(rollbackFor = Exception.class)
    public void expirePendingOrders() {
        OffsetDateTime timestamp = now();
        List<PmPaymentOrder> expired = orderMapper.selectList(
            new LambdaQueryWrapper<PmPaymentOrder>()
                .eq(PmPaymentOrder::getStatus, PaymentConstants.ORDER_STATUS_PENDING)
                .le(PmPaymentOrder::getExpiresAt, timestamp)
                .orderByAsc(PmPaymentOrder::getExpiresAt)
                .last("limit 1000"));
        for (PmPaymentOrder candidate : expired) {
            PmPaymentOrder order = orderMapper.selectByIdForUpdate(
                candidate.getId(),
                candidate.getMerchantId()
            );
            if (order == null
                || !PaymentConstants.ORDER_STATUS_PENDING.equals(order.getStatus())
                || order.getExpiresAt() == null
                || order.getExpiresAt().isAfter(timestamp)) {
                continue;
            }
            String before = order.getStatus();
            order.setStatus(PaymentConstants.ORDER_STATUS_EXPIRED);
            order.setUpdatedAt(timestamp);
            order.setVersion(nextVersion(order.getVersion()));
            orderMapper.updateById(order);
            amountSlotService.startCooling(order.getId());
            audit(order, null, "EXPIRE", before, order.getStatus(), "订单支付时限已到", null);
            webhookOutboxService.enqueueOrderEvent(
                order,
                null,
                "payment.order.expired");
        }
    }

    private PmPaymentOrder createInternal(
        Long merchantId,
        String merchantOrderNo,
        String platform,
        PmQrAsset asset,
        long amountMinor,
        Integer expiresSeconds,
        String subject,
        String customerNote,
        boolean merchantApi
    ) {
        expirePendingOrders();
        PmPaymentOrder existing = findEntityByMerchantOrderNo(merchantId, merchantOrderNo);
        if (existing != null) {
            return requireIdempotentMatch(existing, platform, asset.getId(), amountMinor, merchantApi);
        }
        OffsetDateTime timestamp = now();
        for (int offset = 0; offset <= 99; offset++) {
            PmPaymentOrder order = new PmPaymentOrder();
            order.setId(IdWorker.getId());
            order.setMerchantId(merchantId);
            order.setMerchantOrderNo(merchantOrderNo);
            order.setPlatform(platform);
            order.setQrAssetId(asset.getId());
            order.setRequestedAmountMinor(amountMinor);
            order.setPayableAmountMinor(amountMinor + offset);
            order.setAmountOffsetMinor(offset);
            order.setCurrency("CNY");
            order.setStatus(PaymentConstants.ORDER_STATUS_PENDING);
            order.setConfirmationStatus(PaymentConstants.CONFIRMATION_UNCONFIRMED);
            order.setVersion(0);
            order.setPublicToken(PaymentCrypto.randomSecret());
            order.setSubject(subject);
            order.setCustomerNote(customerNote);
            order.setCreatedAt(timestamp);
            order.setExpiresAt(timestamp.plusSeconds(expiresSeconds == null ? 300 : expiresSeconds));
            order.setUpdatedAt(timestamp);
            if (orderMapper.insertOnConflict(order) > 0) {
                if (!amountSlotService.reserve(order)) {
                    orderMapper.deleteById(order.getId());
                    continue;
                }
                audit(order, null, "CREATE", null, order.getStatus(), "创建动态金额订单", currentUserId());
                return order;
            }
            existing = findEntityByMerchantOrderNo(merchantId, merchantOrderNo);
            if (existing != null) {
                return requireIdempotentMatch(existing, platform, asset.getId(), amountMinor, merchantApi);
            }
        }
        if (merchantApi) {
            throw new MerchantApiException(
                409, "ORDER_AMOUNT_EXHAUSTED", "当前金额附近的分值均被占用", true);
        }
        throw new ServiceException("当前金额附近的 100 个分值均已占用，请稍后重试");
    }

    private void requireManualMatchEventEligible(
        PmPaymentOrder order,
        PmPaymentEvent event
    ) {
        if (!"INCOME".equals(event.getDirection()) || event.getAmountMinor() == null) {
            throw new ServiceException("只有金额有效的收入事件可以补单");
        }
        if (!MANUAL_MATCH_EVENT_STATUSES.contains(event.getStatus())) {
            throw new ServiceException("支付事件当前状态不可用于补单，请刷新候选列表");
        }
        if (PaymentConstants.DUPLICATE_STATUS_CONFIRMED.equals(event.getDuplicateStatus())) {
            throw new ServiceException("已确认重复的支付事件不可用于补单");
        }
        if (eventMapper.countManualMatchOccupations(
            order.getMerchantId(),
            order.getId(),
            event.getId()
        ) > 0) {
            throw new ServiceException("支付事件已匹配其他订单，请刷新候选列表");
        }
    }

    private boolean isManualMatchEventEligible(PmPaymentEvent event) {
        return event != null
            && "INCOME".equals(event.getDirection())
            && event.getAmountMinor() != null
            && MANUAL_MATCH_EVENT_STATUSES.contains(event.getStatus())
            && !PaymentConstants.DUPLICATE_STATUS_CONFIRMED.equals(event.getDuplicateStatus());
    }

    private OrderMatchCandidateVo toMatchCandidate(
        PmPaymentOrder order,
        PmPaymentEvent event
    ) {
        OrderMatchCandidateVo candidate = new OrderMatchCandidateVo();
        candidate.setId(event.getId());
        candidate.setClientEventId(event.getClientEventId());
        candidate.setPlatform(event.getPlatform());
        candidate.setAmountMinor(event.getAmountMinor());
        candidate.setCurrency(event.getCurrency());
        candidate.setEventTime(event.getEventTime());
        candidate.setReceivedAt(event.getReceivedAt());
        candidate.setStatus(event.getStatus());
        candidate.setDuplicateStatus(event.getDuplicateStatus());
        candidate.setExactMatch(
            Objects.equals(order.getPlatform(), event.getPlatform())
                && Objects.equals(order.getPayableAmountMinor(), event.getAmountMinor())
        );
        return candidate;
    }

    private PmPaymentOrder requireIdempotentMatch(
        PmPaymentOrder existing,
        String platform,
        Long qrAssetId,
        long amountMinor,
        boolean merchantApi
    ) {
        if (!existing.getPlatform().equals(platform)
            || !existing.getQrAssetId().equals(qrAssetId)
            || !existing.getRequestedAmountMinor().equals(amountMinor)) {
            if (merchantApi) {
                throw new MerchantApiException(
                    409,
                    "ORDER_CONFLICT",
                    "相同商户订单号已存在且请求参数不同",
                    false);
            }
            throw new ServiceException("商户订单号已存在且请求参数不一致");
        }
        return existing;
    }

    private void cancelInternal(
        PmPaymentOrder order,
        String note,
        Long operatedBy,
        boolean merchantApi
    ) {
        if (PaymentConstants.ORDER_STATUS_CANCELLED.equals(order.getStatus())) {
            return;
        }
        if (!PaymentConstants.ORDER_STATUS_PENDING.equals(order.getStatus())) {
            if (merchantApi) {
                throw new MerchantApiException(
                    409, "ORDER_NOT_CANCELLABLE", "当前订单状态不能取消", false);
            }
            throw new ServiceException("只有待支付订单可以取消");
        }
        String before = order.getStatus();
        OffsetDateTime timestamp = now();
        order.setStatus(PaymentConstants.ORDER_STATUS_CANCELLED);
        order.setCancelledAt(timestamp);
        order.setUpdatedAt(timestamp);
        order.setVersion(nextVersion(order.getVersion()));
        orderMapper.updateById(order);
        amountSlotService.startCooling(order.getId());
        audit(order, null, "CANCEL", before, order.getStatus(), note, operatedBy);
        webhookOutboxService.enqueueOrderEvent(
            order,
            null,
            "payment.order.cancelled");
    }

    private PmPaymentTransaction match(
        PmPaymentOrder order,
        PmPaymentEvent event,
        String action,
        String note,
        Long operatedBy
    ) {
        String before = order.getStatus();
        OffsetDateTime paidAt = event.getEventTime() == null ? event.getReceivedAt() : event.getEventTime();
        order.setStatus(PaymentConstants.ORDER_STATUS_PAID);
        order.setMatchedEventId(event.getId());
        order.setPaidAt(paidAt);
        order.setUpdatedAt(now());
        event.setStatus(PaymentConstants.EVENT_STATUS_MATCHED);
        eventMapper.updateById(event);
        PmPaymentTransaction transaction = transactionService.markMatched(order, event);
        orderMapper.updateById(order);
        amountSlotService.startCooling(order.getId());
        audit(order, event.getId(), action, before, order.getStatus(), note, operatedBy);
        webhookOutboxService.enqueueOrderPaid(order, event);
        return transaction;
    }

    private String renderQrContent(PmPaymentOrder order, PmQrAsset asset) {
        String amount = java.math.BigDecimal.valueOf(order.getPayableAmountMinor(), 2)
            .setScale(2).toPlainString();
        return asset.getQrContentTemplate()
            .replace("{amountMinor}", order.getPayableAmountMinor().toString())
            .replace("{amount}", amount)
            .replace("{orderNo}", order.getMerchantOrderNo());
    }

    private PaymentOrderVo enrich(PaymentOrderVo order, boolean history) {
        if (order == null) {
            return null;
        }
        PmQrAsset asset = qrAssetMapper.selectOne(new LambdaQueryWrapper<PmQrAsset>()
            .eq(PmQrAsset::getId, order.getQrAssetId())
            .eq(PmQrAsset::getMerchantId, order.getMerchantId())
            .last("limit 1"));
        order.setQrAssetName(asset == null ? null : asset.getAssetName());
        order.setPayUrl(publicPayUrl(order.getPublicToken()));
        var slot = amountSlotService.findByOrder(order.getMerchantId(), order.getId());
        if (slot != null) {
            order.setAmountSlotStatus(slot.getStatus());
            order.setAmountSlotCoolingUntil(slot.getCoolingUntil());
        }
        if (history) {
            order.setMatchHistory(auditMapper.selectVoList(
                new LambdaQueryWrapper<PmOrderMatchAudit>()
                    .eq(PmOrderMatchAudit::getMerchantId, order.getMerchantId())
                    .eq(PmOrderMatchAudit::getOrderId, order.getId())
                    .orderByDesc(PmOrderMatchAudit::getOperatedAt)));
        }
        return order;
    }

    private PaymentOrderVo toVo(PmPaymentOrder order) {
        PaymentOrderVo vo = new PaymentOrderVo();
        vo.setId(order.getId());
        vo.setMerchantId(order.getMerchantId());
        vo.setMerchantOrderNo(order.getMerchantOrderNo());
        vo.setPlatform(order.getPlatform());
        vo.setQrAssetId(order.getQrAssetId());
        vo.setRequestedAmountMinor(order.getRequestedAmountMinor());
        vo.setPayableAmountMinor(order.getPayableAmountMinor());
        vo.setAmountOffsetMinor(order.getAmountOffsetMinor());
        vo.setCurrency(order.getCurrency());
        vo.setStatus(order.getStatus());
        vo.setPublicToken(order.getPublicToken());
        vo.setSubject(order.getSubject());
        vo.setCustomerNote(order.getCustomerNote());
        vo.setMatchedEventId(order.getMatchedEventId());
        vo.setTransactionId(order.getTransactionId());
        vo.setConfirmationStatus(order.getConfirmationStatus());
        vo.setConfirmedAt(order.getConfirmedAt());
        vo.setConfirmedBy(order.getConfirmedBy());
        vo.setConfirmationSource(order.getConfirmationSource());
        vo.setConfirmationNote(order.getConfirmationNote());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setExpiresAt(order.getExpiresAt());
        vo.setPaidAt(order.getPaidAt());
        vo.setCancelledAt(order.getCancelledAt());
        vo.setUpdatedAt(order.getUpdatedAt());
        return vo;
    }

    private MerchantOrderVo toMerchantVo(PmPaymentOrder order, PmQrAsset asset) {
        return new MerchantOrderVo(
            order.getMerchantOrderNo(),
            order.getPlatform(),
            asset == null ? null : asset.getAssetCode(),
            order.getRequestedAmountMinor(),
            order.getPayableAmountMinor(),
            order.getCurrency(),
            order.getStatus(),
            publicPayUrl(order.getPublicToken()),
            order.getExpiresAt(),
            order.getPaidAt(),
            order.getCancelledAt(),
            order.getTransactionId(),
            order.getConfirmationStatus(),
            order.getConfirmedAt(),
            order.getConfirmationSource());
    }

    private LambdaQueryWrapper<PmPaymentOrder> buildQuery(PaymentOrderQueryBo bo) {
        Long merchantId = MerchantContext.resolveQueryMerchantId(bo.getMerchantId());
        return new LambdaQueryWrapper<PmPaymentOrder>()
            .eq(merchantId != null, PmPaymentOrder::getMerchantId, merchantId)
            .like(StringUtils.isNotBlank(bo.getMerchantOrderNo()), PmPaymentOrder::getMerchantOrderNo, bo.getMerchantOrderNo())
            .eq(StringUtils.isNotBlank(bo.getPlatform()), PmPaymentOrder::getPlatform, bo.getPlatform())
            .eq(StringUtils.isNotBlank(bo.getStatus()), PmPaymentOrder::getStatus, bo.getStatus())
            .eq(bo.getPayableAmountMinor() != null, PmPaymentOrder::getPayableAmountMinor, bo.getPayableAmountMinor())
            .eq(bo.getMatchedEventId() != null, PmPaymentOrder::getMatchedEventId, bo.getMatchedEventId())
            .ge(bo.getBeginTime() != null, PmPaymentOrder::getCreatedAt, bo.getBeginTime())
            .le(bo.getEndTime() != null, PmPaymentOrder::getCreatedAt, bo.getEndTime());
    }

    private PmPaymentOrder requireAdminOrder(Long id) {
        PmPaymentOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException("支付订单不存在");
        }
        MerchantContext.requireAccessibleMerchant(order.getMerchantId());
        return order;
    }

    private PmPaymentOrder requireByToken(String token) {
        PmPaymentOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PmPaymentOrder>()
            .eq(PmPaymentOrder::getPublicToken, token)
            .last("limit 1"));
        if (order == null) {
            throw new ServiceException("支付订单不存在");
        }
        return order;
    }

    private PmPaymentOrder findEntityByMerchantOrderNo(Long merchantId, String merchantOrderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<PmPaymentOrder>()
            .eq(PmPaymentOrder::getMerchantId, merchantId)
            .eq(PmPaymentOrder::getMerchantOrderNo, merchantOrderNo)
            .last("limit 1"));
    }

    private void audit(
        PmPaymentOrder order,
        Long eventId,
        String action,
        String beforeStatus,
        String afterStatus,
        String note,
        Long operatedBy
    ) {
        PmOrderMatchAudit audit = new PmOrderMatchAudit();
        audit.setId(IdWorker.getId());
        audit.setMerchantId(order.getMerchantId());
        audit.setOrderId(order.getId());
        audit.setEventId(eventId);
        audit.setAction(action);
        audit.setBeforeStatus(beforeStatus);
        audit.setAfterStatus(afterStatus);
        audit.setNote(note);
        audit.setOperatedBy(operatedBy);
        audit.setOperatedAt(now());
        auditMapper.insert(audit);
    }

    private Long currentUserId() {
        try {
            return LoginHelper.isLogin() ? LoginHelper.getUserId() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String generateOrderNo() {
        return "PM" + OffsetDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
            + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private String publicPayUrl(String token) {
        return properties.getPublicBaseUrl().replaceAll("/+$", "") + "/pay/" + token;
    }

    private int nextVersion(Integer version) {
        return version == null ? 1 : version + 1;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
