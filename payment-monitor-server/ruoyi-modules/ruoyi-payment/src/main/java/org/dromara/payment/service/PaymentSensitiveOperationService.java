package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmOrderMatchAudit;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.PmPaymentTransaction;
import org.dromara.payment.mapper.OrderMatchAuditMapper;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.mapper.PaymentTransactionMapper;
import org.dromara.payment.security.StepUpVerificationMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentSensitiveOperationService {
    private final PaymentTransactionMapper transactionMapper;
    private final PaymentOrderMapper orderMapper;
    private final OrderMatchAuditMapper auditMapper;
    private final AmountSlotService amountSlotService;
    private final WebhookOutboxService webhookOutboxService;
    private final SensitiveOperationLogService sensitiveOperationLogService;

    @Transactional(rollbackFor = Exception.class)
    public PmPaymentTransaction reverseConfirmation(
        Long transactionId,
        String reason,
        StepUpVerificationMethod verificationMethod
    ) {
        PmPaymentTransaction selected = transactionMapper.selectById(transactionId);
        if (selected == null) {
            throw new ServiceException("支付交易不存在");
        }
        MerchantContext.requireAccessibleMerchant(selected.getMerchantId());
        Long merchantId = selected.getMerchantId();
        PmPaymentTransaction transaction =
            transactionMapper.selectByIdForUpdate(transactionId, merchantId);
        if (transaction == null) {
            throw new ServiceException("支付交易不存在");
        }
        if (PaymentConstants.TRANSACTION_REVERSED.equals(transaction.getStatus())) {
            return transaction;
        }
        if (transaction.getOrderId() == null
            || (!PaymentConstants.TRANSACTION_MATCHED.equals(transaction.getStatus())
                && !PaymentConstants.TRANSACTION_CONFIRMED.equals(transaction.getStatus())
                && !PaymentConstants.TRANSACTION_RECONCILED.equals(transaction.getStatus()))) {
            throw new ServiceException("只有已匹配或已确认的交易可以撤销确认");
        }
        PmPaymentOrder order = orderMapper.selectByIdForUpdate(
            transaction.getOrderId(), merchantId);
        if (order == null) {
            throw new ServiceException("交易关联订单不存在");
        }

        String beforeSnapshot = sensitiveOperationLogService.snapshot(
            Map.of("transaction", transaction, "order", order));
        Integer originalVersion = transaction.getVersion();
        Long operatorId = currentUserId();
        OffsetDateTime timestamp = now();

        transaction.setStatus(PaymentConstants.TRANSACTION_REVERSED);
        transaction.setConfirmationStatus(PaymentConstants.CONFIRMATION_UNCONFIRMED);
        transaction.setReversedAt(timestamp);
        transaction.setReversedBy(operatorId);
        transaction.setUpdatedAt(timestamp);
        transaction.setVersion(nextVersion(transaction.getVersion()));
        transactionMapper.updateById(transaction);

        String beforeOrderStatus = order.getStatus();
        order.setStatus(PaymentConstants.ORDER_STATUS_CONFLICT);
        order.setConfirmationStatus(PaymentConstants.CONFIRMATION_UNCONFIRMED);
        order.setConfirmedAt(null);
        order.setConfirmedBy(null);
        order.setConfirmationSource("CONFIRMATION_REVOKED");
        order.setConfirmationNote(reason);
        order.setUpdatedAt(timestamp);
        order.setVersion(nextVersion(order.getVersion()));
        orderMapper.updateById(order);
        amountSlotService.reactivate(order.getId());
        audit(
            order,
            order.getMatchedEventId(),
            "CONFLICT",
            beforeOrderStatus,
            order.getStatus(),
            verificationLabel(verificationMethod) + "撤销支付确认：" + reason,
            operatorId);
        webhookOutboxService.enqueueOrderEvent(
            order,
            null,
            "payment.order.confirmation_revoked");

        sensitiveOperationLogService.record(
            merchantId,
            "REVERSE_CONFIRMATION",
            "PAYMENT_TRANSACTION",
            transactionId,
            reason,
            Map.of("transactionId", transactionId, "reason", reason),
            beforeSnapshot,
            Map.of("transaction", transaction, "order", order),
            verificationMethod,
            "REVERSE_CONFIRMATION:" + transactionId + ":" + originalVersion);
        return transaction;
    }

    private String verificationLabel(StepUpVerificationMethod verificationMethod) {
        return verificationMethod == StepUpVerificationMethod.MFA
            ? "MFA 单人确认"
            : "登录会话确认";
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
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("登录状态无效");
        }
        return userId;
    }

    private int nextVersion(Integer version) {
        return version == null ? 1 : version + 1;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
