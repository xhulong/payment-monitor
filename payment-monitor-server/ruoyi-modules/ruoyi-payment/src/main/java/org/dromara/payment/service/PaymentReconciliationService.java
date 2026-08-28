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
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.PmPaymentTransaction;
import org.dromara.payment.domain.PmReconciliationItem;
import org.dromara.payment.domain.PmReconciliationRun;
import org.dromara.payment.domain.PmWebhookOutbox;
import org.dromara.payment.domain.dto.ReconciliationResolveRequest;
import org.dromara.payment.domain.vo.ReconciliationRunDetailVo;
import org.dromara.payment.mapper.MerchantMapper;
import org.dromara.payment.mapper.PaymentEventMapper;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.mapper.PaymentTransactionMapper;
import org.dromara.payment.mapper.ReconciliationItemMapper;
import org.dromara.payment.mapper.ReconciliationRunMapper;
import org.dromara.payment.mapper.WebhookOutboxMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationService {
    private final ReconciliationRunMapper runMapper;
    private final ReconciliationItemMapper itemMapper;
    private final MerchantMapper merchantMapper;
    private final PaymentOrderMapper orderMapper;
    private final PaymentEventMapper eventMapper;
    private final PaymentTransactionMapper transactionMapper;
    private final WebhookOutboxMapper outboxMapper;
    private final PaymentTransactionService transactionService;
    private final MerchantAccessService merchantAccessService;
    private final MerchantDisplayService merchantDisplayService;

    public PmReconciliationRun latest() {
        return latest(null);
    }

    public PmReconciliationRun latest(Long requestedMerchantId) {
        Long merchantId = MerchantContext.resolveQueryMerchantId(requestedMerchantId);
        PmReconciliationRun run = runMapper.selectOne(new LambdaQueryWrapper<PmReconciliationRun>()
            .eq(merchantId != null, PmReconciliationRun::getMerchantId, merchantId)
            .orderByDesc(PmReconciliationRun::getCreatedAt)
            .last("limit 1"));
        enrichRuns(run == null ? List.of() : List.of(run));
        return run;
    }

    public PageResult<PmReconciliationRun> queryRuns(PageQuery pageQuery) {
        return queryRuns(null, pageQuery);
    }

    public PageResult<PmReconciliationRun> queryRuns(Long requestedMerchantId, PageQuery pageQuery) {
        Long merchantId = MerchantContext.resolveQueryMerchantId(requestedMerchantId);
        Page<PmReconciliationRun> page = runMapper.selectPage(
            pageQuery.build(),
            new LambdaQueryWrapper<PmReconciliationRun>()
                .eq(merchantId != null, PmReconciliationRun::getMerchantId, merchantId)
                .orderByDesc(PmReconciliationRun::getCreatedAt));
        enrichRuns(page.getRecords());
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    public ReconciliationRunDetailVo queryRun(Long id) {
        PmReconciliationRun run = runMapper.selectOne(
            new LambdaQueryWrapper<PmReconciliationRun>()
                .eq(PmReconciliationRun::getId, id)
                .last("limit 1"));
        if (run == null) {
            throw new ServiceException("对账运行不存在");
        }
        MerchantContext.requireAccessibleMerchant(run.getMerchantId());
        Long merchantId = run.getMerchantId();
        List<PmReconciliationItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<PmReconciliationItem>()
                .eq(PmReconciliationItem::getMerchantId, merchantId)
                .eq(PmReconciliationItem::getRunId, id)
                .orderByAsc(PmReconciliationItem::getDifferenceType)
                .orderByAsc(PmReconciliationItem::getCreatedAt));
        enrichRuns(List.of(run));
        merchantDisplayService.enrich(
            items,
            PmReconciliationItem::getMerchantId,
            PmReconciliationItem::setMerchantCode,
            PmReconciliationItem::setMerchantName);
        return new ReconciliationRunDetailVo(run, items);
    }

    @Transactional(rollbackFor = Exception.class)
    public PmReconciliationRun run(LocalDate businessDate) {
        return run(null, businessDate);
    }

    @Transactional(rollbackFor = Exception.class)
    public PmReconciliationRun run(Long requestedMerchantId, LocalDate businessDate) {
        Long merchantId = merchantAccessService.requireTargetMerchant(requestedMerchantId, false);
        PmMerchant merchant = requireMerchant(merchantId);
        ZoneId zone = merchantZone(merchant);
        return calculateAndSave(
            merchant,
            businessDate == null ? LocalDate.now(zone) : businessDate,
            currentUserId());
    }

    public PmReconciliationRun previewToday(Long merchantId) {
        PmMerchant merchant = requireMerchant(merchantId);
        PmReconciliationRun preview = calculateSummary(
            merchant,
            LocalDate.now(merchantZone(merchant)),
            null
        );
        boolean attentionRequired = preview.getUnmatchedIncomeCount() > 0
            || preview.getConflictOrderCount() > 0
            || preview.getSuspectedDuplicateCount() > 0
            || preview.getWebhookDeadCount() > 0
            || preview.getAmountDifferenceMinor() != 0;
        preview.setStatus(attentionRequired ? "ATTENTION_REQUIRED" : "BALANCED");
        return preview;
    }

    @Transactional(rollbackFor = Exception.class)
    public PmReconciliationItem resolve(Long itemId, ReconciliationResolveRequest request) {
        PmReconciliationItem selected = itemMapper.selectById(itemId);
        if (selected == null) {
            throw new ServiceException("对账差异不存在");
        }
        MerchantContext.requireAccessibleMerchant(selected.getMerchantId());
        Long merchantId = selected.getMerchantId();
        PmReconciliationItem item = itemMapper.selectByIdForUpdate(itemId, merchantId);
        if (item == null) {
            throw new ServiceException("对账差异不存在");
        }
        if ("RESOLVED".equals(item.getStatus()) || "IGNORED".equals(item.getStatus())) {
            return item;
        }
        if ("RECONCILE".equals(request.getAction())) {
            if (item.getTransactionId() == null || item.getOrderId() == null) {
                throw new ServiceException("当前差异不能升级为已对账");
            }
            PmPaymentTransaction transaction = transactionMapper.selectByIdForUpdate(
                item.getTransactionId(), merchantId);
            PmPaymentOrder order = orderMapper.selectByIdForUpdate(item.getOrderId(), merchantId);
            if (transaction == null || order == null) {
                throw new ServiceException("对账关联交易或订单不存在");
            }
            if (!PaymentConstants.CONFIRMATION_MANUAL.equals(order.getConfirmationStatus())
                && !PaymentConstants.CONFIRMATION_RECONCILED.equals(order.getConfirmationStatus())) {
                throw new ServiceException("订单需先完成人工确认");
            }
            transactionService.markReconciled(transaction, order);
            item.setStatus("RESOLVED");
        } else {
            item.setStatus("IGNORE".equals(request.getAction()) ? "IGNORED" : "RESOLVED");
        }
        OffsetDateTime timestamp = now();
        item.setResolutionAction(request.getAction());
        item.setResolutionNote(request.getNote());
        item.setResolvedBy(currentUserId());
        item.setResolvedAt(timestamp);
        item.setUpdatedAt(timestamp);
        itemMapper.updateById(item);
        refreshRunCounters(item.getRunId(), merchantId);
        return item;
    }

    private void enrichRuns(List<PmReconciliationRun> runs) {
        merchantDisplayService.enrich(
            runs,
            PmReconciliationRun::getMerchantId,
            PmReconciliationRun::setMerchantCode,
            PmReconciliationRun::setMerchantName);
    }

    @Scheduled(cron = "${payment.reconciliation.daily-cron:0 10 0 * * *}", zone = "UTC")
    public void runDaily() {
        for (PmMerchant merchant : merchantMapper.selectList(
            new LambdaQueryWrapper<PmMerchant>()
                .eq(PmMerchant::getStatus, PaymentConstants.DEVICE_STATUS_ENABLED))) {
            try {
                calculateAndSave(
                    merchant,
                    LocalDate.now(merchantZone(merchant)).minusDays(1),
                    null);
            } catch (RuntimeException exception) {
                log.error("每日支付对账失败，merchantId={}", merchant.getId(), exception);
            }
        }
    }

    private PmReconciliationRun calculateAndSave(
        PmMerchant merchant,
        LocalDate businessDate,
        Long createdBy
    ) {
        PmReconciliationRun run = calculateSummary(merchant, businessDate, createdBy);
        run.setRunNo(generateRunNo(businessDate));
        run.setStatus("ATTENTION_REQUIRED");
        runMapper.insert(run);
        List<PmReconciliationItem> items = buildDifferences(merchant, businessDate, run.getId());
        for (PmReconciliationItem item : items) {
            itemMapper.insert(item);
        }
        run.setOpenDifferenceCount((long) items.size());
        run.setResolvedDifferenceCount(0L);
        run.setStatus(items.isEmpty() && run.getAmountDifferenceMinor() == 0
            ? "BALANCED"
            : "ATTENTION_REQUIRED");
        runMapper.updateById(run);
        autoReconcileCleanManualOrders(merchant, businessDate, items);
        return run;
    }

    private PmReconciliationRun calculateSummary(
        PmMerchant merchant,
        LocalDate businessDate,
        Long createdBy
    ) {
        ZoneId zone = merchantZone(merchant);
        OffsetDateTime begin = businessDate.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = businessDate.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        List<PmPaymentOrder> paidOrders = orderMapper.selectList(
            new LambdaQueryWrapper<PmPaymentOrder>()
                .eq(PmPaymentOrder::getMerchantId, merchant.getId())
                .eq(PmPaymentOrder::getStatus, PaymentConstants.ORDER_STATUS_PAID)
                .ge(PmPaymentOrder::getPaidAt, begin)
                .lt(PmPaymentOrder::getPaidAt, end));
        List<PmPaymentTransaction> transactions = transactionMapper.selectList(
            new LambdaQueryWrapper<PmPaymentTransaction>()
                .eq(PmPaymentTransaction::getMerchantId, merchant.getId())
                .ge(PmPaymentTransaction::getObservedAt, begin)
                .lt(PmPaymentTransaction::getObservedAt, end));
        long paidAmount = paidOrders.stream()
            .map(PmPaymentOrder::getPayableAmountMinor)
            .filter(Objects::nonNull)
            .mapToLong(Long::longValue)
            .sum();
        long matchedAmount = transactions.stream()
            .filter(item -> item.getOrderId() != null)
            .map(PmPaymentTransaction::getAmountMinor)
            .filter(Objects::nonNull)
            .mapToLong(Long::longValue)
            .sum();
        long unmatchedAmount = transactions.stream()
            .filter(item -> item.getOrderId() == null)
            .map(PmPaymentTransaction::getAmountMinor)
            .filter(Objects::nonNull)
            .mapToLong(Long::longValue)
            .sum();
        long conflictOrders = orderMapper.selectCount(
            new LambdaQueryWrapper<PmPaymentOrder>()
                .eq(PmPaymentOrder::getMerchantId, merchant.getId())
                .eq(PmPaymentOrder::getStatus, PaymentConstants.ORDER_STATUS_CONFLICT)
                .ge(PmPaymentOrder::getUpdatedAt, begin)
                .lt(PmPaymentOrder::getUpdatedAt, end));
        long suspectedDuplicates = eventMapper.selectCount(
            new LambdaQueryWrapper<PmPaymentEvent>()
                .eq(PmPaymentEvent::getMerchantId, merchant.getId())
                .eq(PmPaymentEvent::getDuplicateStatus, PaymentConstants.DUPLICATE_STATUS_SUSPECTED)
                .ge(PmPaymentEvent::getReceivedAt, begin)
                .lt(PmPaymentEvent::getReceivedAt, end));
        long webhookDead = outboxMapper.selectCount(
            new LambdaQueryWrapper<PmWebhookOutbox>()
                .eq(PmWebhookOutbox::getMerchantId, merchant.getId())
                .eq(PmWebhookOutbox::getStatus, "DEAD")
                .ge(PmWebhookOutbox::getCreatedAt, begin)
                .lt(PmWebhookOutbox::getCreatedAt, end));
        OffsetDateTime timestamp = now();
        PmReconciliationRun result = new PmReconciliationRun();
        result.setId(IdWorker.getId());
        result.setMerchantId(merchant.getId());
        result.setRunNo("PREVIEW-" + result.getId());
        result.setBusinessDate(businessDate);
        result.setTimezone(zone.getId());
        result.setStatus("CALCULATED");
        result.setPaidOrderCount((long) paidOrders.size());
        result.setPaidOrderAmountMinor(paidAmount);
        result.setMatchedIncomeCount(transactions.stream().filter(item -> item.getOrderId() != null).count());
        result.setMatchedIncomeAmountMinor(matchedAmount);
        result.setUnmatchedIncomeCount(transactions.stream().filter(item -> item.getOrderId() == null).count());
        result.setUnmatchedIncomeAmountMinor(unmatchedAmount);
        result.setConflictOrderCount(conflictOrders);
        result.setSuspectedDuplicateCount(suspectedDuplicates);
        result.setWebhookDeadCount(webhookDead);
        result.setAmountDifferenceMinor(matchedAmount - paidAmount);
        result.setOpenDifferenceCount(0L);
        result.setResolvedDifferenceCount(0L);
        result.setVersion(0);
        result.setCreatedBy(createdBy);
        result.setCreatedAt(timestamp);
        result.setCompletedAt(timestamp);
        return result;
    }

    private List<PmReconciliationItem> buildDifferences(
        PmMerchant merchant,
        LocalDate businessDate,
        Long runId
    ) {
        ZoneId zone = merchantZone(merchant);
        OffsetDateTime begin = businessDate.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = businessDate.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime timestamp = now();
        List<PmReconciliationItem> items = new ArrayList<>();

        List<PmPaymentTransaction> transactions = transactionMapper.selectList(
            new LambdaQueryWrapper<PmPaymentTransaction>()
                .eq(PmPaymentTransaction::getMerchantId, merchant.getId())
                .ge(PmPaymentTransaction::getObservedAt, begin)
                .lt(PmPaymentTransaction::getObservedAt, end));
        for (PmPaymentTransaction transaction : transactions) {
            if (transaction.getOrderId() == null
                && PaymentConstants.TRANSACTION_OBSERVED.equals(transaction.getStatus())) {
                PmPaymentOrder lateOrder = findLateTerminalOrder(transaction);
                String differenceType = lateOrder == null
                    ? "UNMATCHED_INCOME"
                    : "LATE_PAYMENT";
                items.add(item(
                    merchant.getId(), runId, differenceType,
                    lateOrder == null ? null : lateOrder.getId(),
                    transaction.getEventId(), transaction.getId(), null,
                    transaction.getAmountMinor(),
                    lateOrder == null
                        ? "收入通知未匹配订单"
                        : "付款发生在订单取消或过期之后，未自动匹配新订单",
                    timestamp));
            }
        }

        List<PmPaymentOrder> paidOrders = orderMapper.selectList(
            new LambdaQueryWrapper<PmPaymentOrder>()
                .eq(PmPaymentOrder::getMerchantId, merchant.getId())
                .eq(PmPaymentOrder::getStatus, PaymentConstants.ORDER_STATUS_PAID)
                .ge(PmPaymentOrder::getPaidAt, begin)
                .lt(PmPaymentOrder::getPaidAt, end));
        for (PmPaymentOrder order : paidOrders) {
            if (PaymentConstants.CONFIRMATION_NOTIFICATION.equals(order.getConfirmationStatus())
                && order.getPaidAt() != null
                && order.getPaidAt().isBefore(timestamp.minusMinutes(30))) {
                items.add(item(
                    merchant.getId(), runId, "NOTIFICATION_UNCONFIRMED",
                    order.getId(), order.getMatchedEventId(), order.getTransactionId(), null,
                    order.getPayableAmountMinor(), "通知确认超过 30 分钟仍未人工确认", timestamp));
            }
            if (order.getTransactionId() != null) {
                PmPaymentTransaction transaction = transactionMapper.selectById(order.getTransactionId());
                if (transaction != null
                    && !Objects.equals(order.getPayableAmountMinor(), transaction.getAmountMinor())) {
                    items.add(item(
                        merchant.getId(), runId, "AMOUNT_MISMATCH",
                        order.getId(), transaction.getEventId(), transaction.getId(), null,
                        transaction.getAmountMinor(), "订单金额与支付交易金额不一致", timestamp));
                }
            }
        }

        for (PmPaymentOrder order : orderMapper.selectList(
            new LambdaQueryWrapper<PmPaymentOrder>()
                .eq(PmPaymentOrder::getMerchantId, merchant.getId())
                .eq(PmPaymentOrder::getStatus, PaymentConstants.ORDER_STATUS_CONFLICT)
                .ge(PmPaymentOrder::getUpdatedAt, begin)
                .lt(PmPaymentOrder::getUpdatedAt, end))) {
            items.add(item(
                merchant.getId(), runId, "CONFLICT_ORDER",
                order.getId(), order.getMatchedEventId(), order.getTransactionId(), null,
                order.getPayableAmountMinor(), "订单处于冲突状态", timestamp));
        }

        for (PmPaymentEvent event : eventMapper.selectList(
            new LambdaQueryWrapper<PmPaymentEvent>()
                .eq(PmPaymentEvent::getMerchantId, merchant.getId())
                .eq(PmPaymentEvent::getDuplicateStatus, PaymentConstants.DUPLICATE_STATUS_SUSPECTED)
                .ge(PmPaymentEvent::getReceivedAt, begin)
                .lt(PmPaymentEvent::getReceivedAt, end))) {
            items.add(item(
                merchant.getId(), runId, "SUSPECTED_DUPLICATE",
                null, event.getId(), null, null,
                event.getAmountMinor(), "支付事件被标记为疑似重复", timestamp));
        }

        for (PmWebhookOutbox outbox : outboxMapper.selectList(
            new LambdaQueryWrapper<PmWebhookOutbox>()
                .eq(PmWebhookOutbox::getMerchantId, merchant.getId())
                .eq(PmWebhookOutbox::getStatus, "DEAD")
                .ge(PmWebhookOutbox::getCreatedAt, begin)
                .lt(PmWebhookOutbox::getCreatedAt, end))) {
            items.add(item(
                merchant.getId(), runId, "DEAD_WEBHOOK",
                "PAYMENT_ORDER".equals(outbox.getAggregateType()) ? outbox.getAggregateId() : null,
                null, null, outbox.getId(), null, "Webhook 投递已进入 DEAD", timestamp));
        }
        return items;
    }

    private PmPaymentOrder findLateTerminalOrder(PmPaymentTransaction transaction) {
        if (transaction.getObservedAt() == null) {
            return null;
        }
        List<PmPaymentOrder> candidates = orderMapper.selectList(
            new LambdaQueryWrapper<PmPaymentOrder>()
                .eq(PmPaymentOrder::getMerchantId, transaction.getMerchantId())
                .eq(PmPaymentOrder::getPlatform, transaction.getPlatform())
                .eq(PmPaymentOrder::getPayableAmountMinor, transaction.getAmountMinor())
                .in(PmPaymentOrder::getStatus, List.of(
                    PaymentConstants.ORDER_STATUS_CANCELLED,
                    PaymentConstants.ORDER_STATUS_EXPIRED))
                .orderByDesc(PmPaymentOrder::getUpdatedAt)
                .last("limit 20"));
        return candidates.stream()
            .filter(order -> {
                OffsetDateTime terminalAt = PaymentConstants.ORDER_STATUS_CANCELLED.equals(order.getStatus())
                    ? order.getCancelledAt()
                    : order.getExpiresAt();
                return terminalAt != null && !transaction.getObservedAt().isBefore(terminalAt);
            })
            .findFirst()
            .orElse(null);
    }

    private void autoReconcileCleanManualOrders(
        PmMerchant merchant,
        LocalDate businessDate,
        List<PmReconciliationItem> items
    ) {
        Set<Long> blockedOrders = new HashSet<>();
        items.stream().map(PmReconciliationItem::getOrderId).filter(Objects::nonNull)
            .forEach(blockedOrders::add);
        ZoneId zone = merchantZone(merchant);
        OffsetDateTime begin = businessDate.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = businessDate.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        List<PmPaymentOrder> candidates = orderMapper.selectList(
            new LambdaQueryWrapper<PmPaymentOrder>()
                .eq(PmPaymentOrder::getMerchantId, merchant.getId())
                .eq(PmPaymentOrder::getStatus, PaymentConstants.ORDER_STATUS_PAID)
                .eq(PmPaymentOrder::getConfirmationStatus, PaymentConstants.CONFIRMATION_MANUAL)
                .ge(PmPaymentOrder::getPaidAt, begin)
                .lt(PmPaymentOrder::getPaidAt, end));
        for (PmPaymentOrder order : candidates) {
            if (blockedOrders.contains(order.getId()) || order.getTransactionId() == null) {
                continue;
            }
            PmPaymentTransaction transaction = transactionMapper.selectByIdForUpdate(
                order.getTransactionId(), merchant.getId());
            PmPaymentOrder lockedOrder = orderMapper.selectByIdForUpdate(order.getId(), merchant.getId());
            if (transaction != null && lockedOrder != null) {
                transactionService.markReconciled(transaction, lockedOrder);
            }
        }
    }

    private PmReconciliationItem item(
        Long merchantId,
        Long runId,
        String type,
        Long orderId,
        Long eventId,
        Long transactionId,
        Long outboxId,
        Long amountMinor,
        String description,
        OffsetDateTime timestamp
    ) {
        PmReconciliationItem item = new PmReconciliationItem();
        item.setId(IdWorker.getId());
        item.setMerchantId(merchantId);
        item.setRunId(runId);
        item.setDifferenceType(type);
        item.setStatus("OPEN");
        item.setOrderId(orderId);
        item.setEventId(eventId);
        item.setTransactionId(transactionId);
        item.setWebhookOutboxId(outboxId);
        item.setAmountMinor(amountMinor);
        item.setDescription(description);
        item.setCreatedAt(timestamp);
        item.setUpdatedAt(timestamp);
        return item;
    }

    private void refreshRunCounters(Long runId, Long merchantId) {
        PmReconciliationRun run = runMapper.selectOne(
            new LambdaQueryWrapper<PmReconciliationRun>()
                .eq(PmReconciliationRun::getId, runId)
                .eq(PmReconciliationRun::getMerchantId, merchantId)
                .last("limit 1"));
        if (run == null) {
            return;
        }
        long open = itemMapper.selectCount(new LambdaQueryWrapper<PmReconciliationItem>()
            .eq(PmReconciliationItem::getRunId, runId)
            .eq(PmReconciliationItem::getStatus, "OPEN"));
        long resolved = itemMapper.selectCount(new LambdaQueryWrapper<PmReconciliationItem>()
            .eq(PmReconciliationItem::getRunId, runId)
            .in(PmReconciliationItem::getStatus, List.of("RESOLVED", "IGNORED")));
        run.setOpenDifferenceCount(open);
        run.setResolvedDifferenceCount(resolved);
        run.setStatus(open == 0 && run.getAmountDifferenceMinor() == 0 ? "BALANCED" : "ATTENTION_REQUIRED");
        run.setVersion(run.getVersion() == null ? 1 : run.getVersion() + 1);
        runMapper.updateById(run);
    }

    private PmMerchant requireMerchant(Long merchantId) {
        PmMerchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new ServiceException("商户不存在");
        }
        return merchant;
    }

    private ZoneId merchantZone(PmMerchant merchant) {
        return ZoneId.of(StringUtils.blankToDefault(merchant.getTimezone(), "Asia/Shanghai"));
    }

    private String generateRunNo(LocalDate businessDate) {
        return "RCN-" + businessDate.format(DateTimeFormatter.BASIC_ISO_DATE)
            + "-" + now().format(DateTimeFormatter.ofPattern("HHmmssSSS"))
            + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private Long currentUserId() {
        try {
            return LoginHelper.isLogin() ? LoginHelper.getUserId() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
