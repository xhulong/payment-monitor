package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.PmPaymentTransaction;
import org.dromara.payment.domain.bo.PaymentTransactionQueryBo;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.mapper.PaymentTransactionMapper;
import org.dromara.payment.security.StepUpVerificationMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {
    private final PaymentTransactionMapper transactionMapper;
    private final PaymentOrderMapper orderMapper;
    private final WebhookOutboxService webhookOutboxService;
    private final MerchantDisplayService merchantDisplayService;

    @Transactional(rollbackFor = Exception.class)
    public PmPaymentTransaction observe(PmPaymentEvent event) {
        if (!"INCOME".equals(event.getDirection()) || event.getAmountMinor() == null) {
            return null;
        }
        OffsetDateTime timestamp = now();
        PmPaymentTransaction transaction = new PmPaymentTransaction();
        transaction.setId(IdWorker.getId());
        transaction.setMerchantId(event.getMerchantId());
        transaction.setEventId(event.getId());
        transaction.setPlatform(event.getPlatform());
        transaction.setAmountMinor(event.getAmountMinor());
        transaction.setCurrency(StringUtils.blankToDefault(event.getCurrency(), "CNY"));
        transaction.setStatus(PaymentConstants.TRANSACTION_OBSERVED);
        transaction.setConfirmationStatus(PaymentConstants.CONFIRMATION_UNCONFIRMED);
        transaction.setObservedAt(event.getEventTime() == null ? event.getReceivedAt() : event.getEventTime());
        transaction.setVersion(0);
        transaction.setCreatedAt(timestamp);
        transaction.setUpdatedAt(timestamp);
        if (transactionMapper.insertOnConflict(transaction) > 0) {
            webhookOutboxService.enqueueTransactionObserved(transaction, event);
            return transaction;
        }
        return transactionMapper.selectOne(new LambdaQueryWrapper<PmPaymentTransaction>()
            .eq(PmPaymentTransaction::getEventId, event.getId())
            .last("limit 1"));
    }

    @Transactional(rollbackFor = Exception.class)
    public PmPaymentTransaction markMatched(PmPaymentOrder order, PmPaymentEvent event) {
        PmPaymentTransaction transaction = transactionMapper.selectByEventForUpdate(
            event.getId(), event.getMerchantId());
        if (transaction == null) {
            transaction = observe(event);
            transaction = transactionMapper.selectByEventForUpdate(event.getId(), event.getMerchantId());
        }
        OffsetDateTime timestamp = now();
        transaction.setOrderId(order.getId());
        transaction.setStatus(PaymentConstants.TRANSACTION_MATCHED);
        transaction.setConfirmationStatus(PaymentConstants.CONFIRMATION_NOTIFICATION);
        transaction.setMatchedAt(timestamp);
        transaction.setUpdatedAt(timestamp);
        transaction.setVersion(nextVersion(transaction.getVersion()));
        transactionMapper.updateById(transaction);

        order.setTransactionId(transaction.getId());
        order.setConfirmationStatus(PaymentConstants.CONFIRMATION_NOTIFICATION);
        order.setConfirmedAt(order.getPaidAt() == null ? timestamp : order.getPaidAt());
        order.setConfirmationSource("PAYMENT_NOTIFICATION");
        order.setVersion(nextVersion(order.getVersion()));
        return transaction;
    }

    public PageResult<PmPaymentTransaction> queryPage(
        PaymentTransactionQueryBo bo,
        PageQuery pageQuery
    ) {
        Long merchantId = MerchantContext.resolveQueryMerchantId(bo.getMerchantId());
        LambdaQueryWrapper<PmPaymentTransaction> wrapper =
            new LambdaQueryWrapper<PmPaymentTransaction>()
                .eq(merchantId != null, PmPaymentTransaction::getMerchantId, merchantId)
                .eq(StringUtils.isNotBlank(bo.getPlatform()), PmPaymentTransaction::getPlatform, bo.getPlatform())
                .eq(StringUtils.isNotBlank(bo.getStatus()), PmPaymentTransaction::getStatus, bo.getStatus())
                .eq(StringUtils.isNotBlank(bo.getConfirmationStatus()),
                    PmPaymentTransaction::getConfirmationStatus,
                    bo.getConfirmationStatus())
                .eq(bo.getOrderId() != null, PmPaymentTransaction::getOrderId, bo.getOrderId())
                .eq(bo.getEventId() != null, PmPaymentTransaction::getEventId, bo.getEventId())
                .ge(bo.getBeginTime() != null, PmPaymentTransaction::getObservedAt, bo.getBeginTime())
                .le(bo.getEndTime() != null, PmPaymentTransaction::getObservedAt, bo.getEndTime())
                .orderByDesc(PmPaymentTransaction::getObservedAt);
        Page<PmPaymentTransaction> page = transactionMapper.selectPage(pageQuery.build(), wrapper);
        merchantDisplayService.enrich(
            page.getRecords(),
            PmPaymentTransaction::getMerchantId,
            PmPaymentTransaction::setMerchantCode,
            PmPaymentTransaction::setMerchantName);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    public PmPaymentTransaction queryById(Long id) {
        PmPaymentTransaction transaction = transactionMapper.selectOne(
            new LambdaQueryWrapper<PmPaymentTransaction>()
                .eq(PmPaymentTransaction::getId, id)
                .last("limit 1"));
        if (transaction == null) {
            throw new ServiceException("支付交易不存在");
        }
        MerchantContext.requireAccessibleMerchant(transaction.getMerchantId());
        merchantDisplayService.enrich(
            List.of(transaction),
            PmPaymentTransaction::getMerchantId,
            PmPaymentTransaction::setMerchantCode,
            PmPaymentTransaction::setMerchantName);
        return transaction;
    }

    @Transactional(rollbackFor = Exception.class)
    public PmPaymentTransaction confirm(Long id, String note) {
        PmPaymentTransaction selected = transactionMapper.selectById(id);
        if (selected == null) {
            throw new ServiceException("支付交易不存在");
        }
        MerchantContext.requireAccessibleMerchant(selected.getMerchantId());
        Long merchantId = selected.getMerchantId();
        PmPaymentTransaction transaction = transactionMapper.selectByIdForUpdate(id, merchantId);
        if (transaction == null) {
            throw new ServiceException("支付交易不存在");
        }
        if (PaymentConstants.TRANSACTION_REVERSED.equals(transaction.getStatus())
            || PaymentConstants.TRANSACTION_REJECTED.equals(transaction.getStatus())) {
            throw new ServiceException("当前交易状态不可确认");
        }
        if (PaymentConstants.CONFIRMATION_MANUAL.equals(transaction.getConfirmationStatus())
            || PaymentConstants.CONFIRMATION_RECONCILED.equals(transaction.getConfirmationStatus())) {
            return transaction;
        }
        if (transaction.getOrderId() == null) {
            throw new ServiceException("未匹配订单的交易不可人工确认");
        }
        PmPaymentOrder order = orderMapper.selectByIdForUpdate(transaction.getOrderId(), merchantId);
        if (order == null || !PaymentConstants.ORDER_STATUS_PAID.equals(order.getStatus())) {
            throw new ServiceException("交易关联订单不是已支付状态");
        }
        if (!Objects.equals(transaction.getPlatform(), order.getPlatform())
            || !Objects.equals(transaction.getAmountMinor(), order.getPayableAmountMinor())) {
            throw new ServiceException("交易与订单平台或金额不一致，需要强制补单并完成敏感操作确认");
        }
        OffsetDateTime timestamp = now();
        Long userId = currentUserId();
        transaction.setStatus(PaymentConstants.TRANSACTION_CONFIRMED);
        transaction.setConfirmationStatus(PaymentConstants.CONFIRMATION_MANUAL);
        transaction.setConfirmedAt(timestamp);
        transaction.setConfirmedBy(userId);
        transaction.setUpdatedAt(timestamp);
        transaction.setVersion(nextVersion(transaction.getVersion()));
        transactionMapper.updateById(transaction);

        order.setConfirmationStatus(PaymentConstants.CONFIRMATION_MANUAL);
        order.setConfirmedAt(timestamp);
        order.setConfirmedBy(userId);
        order.setConfirmationSource("MANUAL_CONFIRMATION");
        order.setConfirmationNote(note);
        order.setUpdatedAt(timestamp);
        order.setVersion(nextVersion(order.getVersion()));
        orderMapper.updateById(order);
        webhookOutboxService.enqueueOrderEvent(order, null, "payment.order.confirmed");
        return transaction;
    }

    @Transactional(rollbackFor = Exception.class)
    public PmPaymentTransaction markConfirmedBySensitiveOperation(
        PmPaymentTransaction transaction,
        PmPaymentOrder order,
        Long operator,
        String note,
        StepUpVerificationMethod verificationMethod
    ) {
        if (PaymentConstants.CONFIRMATION_RECONCILED.equals(transaction.getConfirmationStatus())
            || PaymentConstants.CONFIRMATION_RECONCILED.equals(order.getConfirmationStatus())) {
            return transaction;
        }
        if (PaymentConstants.CONFIRMATION_MANUAL.equals(transaction.getConfirmationStatus())
            && PaymentConstants.CONFIRMATION_MANUAL.equals(order.getConfirmationStatus())) {
            return transaction;
        }
        if (!Objects.equals(transaction.getOrderId(), order.getId())) {
            throw new ServiceException("交易与订单不匹配");
        }
        OffsetDateTime timestamp = now();
        transaction.setStatus(PaymentConstants.TRANSACTION_CONFIRMED);
        transaction.setConfirmationStatus(PaymentConstants.CONFIRMATION_MANUAL);
        transaction.setConfirmedAt(timestamp);
        transaction.setConfirmedBy(operator);
        transaction.setUpdatedAt(timestamp);
        transaction.setVersion(nextVersion(transaction.getVersion()));
        transactionMapper.updateById(transaction);

        order.setConfirmationStatus(PaymentConstants.CONFIRMATION_MANUAL);
        order.setConfirmedAt(timestamp);
        order.setConfirmedBy(operator);
        order.setConfirmationSource(
            verificationMethod == StepUpVerificationMethod.MFA
                ? "MFA_SINGLE_CONFIRMATION"
                : "SESSION_SINGLE_CONFIRMATION"
        );
        order.setConfirmationNote(note);
        order.setUpdatedAt(timestamp);
        order.setVersion(nextVersion(order.getVersion()));
        orderMapper.updateById(order);
        webhookOutboxService.enqueueOrderEvent(order, null, "payment.order.confirmed");
        return transaction;
    }

    @Transactional(rollbackFor = Exception.class)
    public void rejectByEvent(Long merchantId, Long eventId, String reason) {
        PmPaymentTransaction transaction = transactionMapper.selectByEventForUpdate(eventId, merchantId);
        if (transaction == null || transaction.getOrderId() != null) {
            return;
        }
        transaction.setStatus(PaymentConstants.TRANSACTION_REJECTED);
        transaction.setRejectionReason(reason);
        transaction.setUpdatedAt(now());
        transaction.setVersion(nextVersion(transaction.getVersion()));
        transactionMapper.updateById(transaction);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markReconciled(PmPaymentTransaction transaction, PmPaymentOrder order) {
        if (PaymentConstants.TRANSACTION_RECONCILED.equals(transaction.getStatus())
            && PaymentConstants.CONFIRMATION_RECONCILED.equals(transaction.getConfirmationStatus())
            && PaymentConstants.CONFIRMATION_RECONCILED.equals(order.getConfirmationStatus())) {
            return;
        }
        OffsetDateTime timestamp = now();
        transaction.setStatus(PaymentConstants.TRANSACTION_RECONCILED);
        transaction.setConfirmationStatus(PaymentConstants.CONFIRMATION_RECONCILED);
        transaction.setReconciledAt(timestamp);
        transaction.setUpdatedAt(timestamp);
        transaction.setVersion(nextVersion(transaction.getVersion()));
        transactionMapper.updateById(transaction);
        order.setConfirmationStatus(PaymentConstants.CONFIRMATION_RECONCILED);
        order.setConfirmedAt(timestamp);
        order.setConfirmationSource("INTERNAL_RECONCILIATION");
        order.setUpdatedAt(timestamp);
        order.setVersion(nextVersion(order.getVersion()));
        orderMapper.updateById(order);
        webhookOutboxService.enqueueOrderEvent(order, null, "payment.order.reconciled");
    }

    private Long currentUserId() {
        try {
            return LoginHelper.isLogin() ? LoginHelper.getUserId() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private int nextVersion(Integer version) {
        return version == null ? 1 : version + 1;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
