package org.dromara.payment.integration.epay.callback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.integration.epay.application.PaymentIntegrationService;
import org.dromara.payment.integration.epay.domain.PmExternalOrderBinding;
import org.dromara.payment.integration.epay.domain.PmPaymentIntegration;
import org.dromara.payment.integration.epay.domain.PmProtocolCallbackOutbox;
import org.dromara.payment.integration.epay.mapper.ExternalOrderBindingMapper;
import org.dromara.payment.integration.epay.mapper.ProtocolCallbackOutboxMapper;
import org.dromara.payment.integration.epay.protocol.EpayAmounts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProtocolCallbackOutboxService {
    private final ExternalOrderBindingMapper bindingMapper;
    private final ProtocolCallbackOutboxMapper outboxMapper;
    private final PaymentIntegrationService integrationService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void onOrderEvent(PmPaymentOrder order, String eventType) {
        PmExternalOrderBinding binding = bindingMapper.selectOne(
            new LambdaQueryWrapper<PmExternalOrderBinding>()
                .eq(PmExternalOrderBinding::getOrderId, order.getId())
                .last("limit 1"));
        if (binding == null || !binding.getMerchantId().equals(order.getMerchantId())) {
            return;
        }
        if ("payment.order.confirmation_revoked".equals(eventType)) {
            markConfirmationRevoked(binding);
            return;
        }
        if (!Set.of("payment.order.paid", "payment.order.confirmed", "payment.order.reconciled")
            .contains(eventType) || !eligible(binding, order)) {
            return;
        }
        PmPaymentIntegration integration = integrationService.requireInternal(binding.getIntegrationId());
        OffsetDateTime timestamp = now();
        PmProtocolCallbackOutbox outbox = new PmProtocolCallbackOutbox();
        outbox.setId(IdWorker.getId());
        outbox.setDeliveryId(UUID.randomUUID().toString());
        outbox.setEventId(businessEventId(binding.getId()));
        outbox.setMerchantId(binding.getMerchantId());
        outbox.setIntegrationId(binding.getIntegrationId());
        outbox.setBindingId(binding.getId());
        outbox.setCallbackKind("TRADE_SUCCESS");
        outbox.setTargetUrl(binding.getNotifyUrl());
        outbox.setRequestMethod(binding.getNotifyMethod());
        outbox.setContentType("POST".equals(binding.getNotifyMethod())
            ? "application/x-www-form-urlencoded; charset=utf-8" : "application/x-www-form-urlencoded");
        outbox.setCredentialVersion(binding.getCredentialVersion());
        outbox.setUnsignedParams(writeParams(notificationParams(integration, binding, order)));
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(timestamp);
        outbox.setStrictAcknowledged(false);
        outbox.setCreatedAt(timestamp);
        outbox.setUpdatedAt(timestamp);
        outboxMapper.insertOnConflict(outbox);
    }

    private void markConfirmationRevoked(PmExternalOrderBinding binding) {
        long delivered = outboxMapper.selectCount(new LambdaQueryWrapper<PmProtocolCallbackOutbox>()
            .eq(PmProtocolCallbackOutbox::getBindingId, binding.getId())
            .eq(PmProtocolCallbackOutbox::getStatus, "DELIVERED"));
        if (delivered == 0 || "CONFIRMATION_REVOKED".equals(binding.getRiskStatus())) {
            return;
        }
        binding.setRiskStatus("CONFIRMATION_REVOKED");
        binding.setRiskReason("支付成功通知已送达后，内部支付确认被撤销，请人工联系接入方处理");
        binding.setUpdatedAt(now());
        bindingMapper.updateById(binding);
    }

    private boolean eligible(PmExternalOrderBinding binding, PmPaymentOrder order) {
        if (!PaymentConstants.ORDER_STATUS_PAID.equals(order.getStatus())) {
            return false;
        }
        return switch (binding.getCallbackPolicy()) {
            case "NOTIFICATION_MATCHED" -> Set.of(
                PaymentConstants.CONFIRMATION_NOTIFICATION,
                PaymentConstants.CONFIRMATION_MANUAL,
                PaymentConstants.CONFIRMATION_RECONCILED).contains(order.getConfirmationStatus());
            case "MANUAL_CONFIRMED" -> Set.of(
                PaymentConstants.CONFIRMATION_MANUAL,
                PaymentConstants.CONFIRMATION_RECONCILED).contains(order.getConfirmationStatus());
            case "RECONCILED" -> PaymentConstants.CONFIRMATION_RECONCILED.equals(order.getConfirmationStatus());
            default -> false;
        };
    }

    private Map<String, String> notificationParams(
        PmPaymentIntegration integration,
        PmExternalOrderBinding binding,
        PmPaymentOrder order
    ) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", integration.getPid());
        params.put("trade_no", binding.getGatewayTradeNo());
        params.put("out_trade_no", binding.getExternalOrderNo());
        params.put("type", binding.getPayType());
        params.put("name", order.getSubject() == null ? "支付订单" : order.getSubject());
        params.put("money", EpayAmounts.toYuan(binding.getRequestAmountMinor()));
        params.put("trade_status", "TRADE_SUCCESS");
        if (binding.getPassthroughParam() != null) {
            params.put("param", binding.getPassthroughParam());
        }
        return params;
    }

    private String writeParams(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception exception) {
            throw new IllegalStateException("生成易支付通知参数失败", exception);
        }
    }

    private String businessEventId(Long bindingId) {
        return UUID.nameUUIDFromBytes(
            ("EPAY:TRADE_SUCCESS:" + bindingId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}