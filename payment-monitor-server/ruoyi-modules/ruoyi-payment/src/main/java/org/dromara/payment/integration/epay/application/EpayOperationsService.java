package org.dromara.payment.integration.epay.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.integration.epay.domain.PmExternalOrderBinding;
import org.dromara.payment.integration.epay.domain.PmPaymentIntegration;
import org.dromara.payment.integration.epay.domain.PmProtocolCallbackDeliveryLog;
import org.dromara.payment.integration.epay.domain.PmProtocolCallbackOutbox;
import org.dromara.payment.integration.epay.domain.bo.ExternalOrderQueryBo;
import org.dromara.payment.integration.epay.domain.bo.ProtocolCallbackQueryBo;
import org.dromara.payment.integration.epay.domain.vo.ExternalOrderVo;
import org.dromara.payment.integration.epay.domain.vo.ProtocolCallbackVo;
import org.dromara.payment.integration.epay.mapper.ExternalOrderBindingMapper;
import org.dromara.payment.integration.epay.mapper.ProtocolCallbackDeliveryLogMapper;
import org.dromara.payment.integration.epay.mapper.ProtocolCallbackOutboxMapper;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.service.MerchantDisplayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EpayOperationsService {
    private final ExternalOrderBindingMapper bindingMapper;
    private final ProtocolCallbackOutboxMapper outboxMapper;
    private final ProtocolCallbackDeliveryLogMapper deliveryLogMapper;
    private final PaymentOrderMapper orderMapper;
    private final PaymentIntegrationService integrationService;
    private final MerchantDisplayService merchantDisplayService;

    public PageResult<ExternalOrderVo> externalOrders(ExternalOrderQueryBo bo, PageQuery pageQuery) {
        Long merchantId = MerchantContext.resolveQueryMerchantId(bo.getMerchantId());
        var wrapper = new LambdaQueryWrapper<PmExternalOrderBinding>()
            .eq(merchantId != null, PmExternalOrderBinding::getMerchantId, merchantId)
            .eq(bo.getIntegrationId() != null, PmExternalOrderBinding::getIntegrationId, bo.getIntegrationId())
            .eq(StringUtils.isNotBlank(bo.getExternalOrderNo()),
                PmExternalOrderBinding::getExternalOrderNo, bo.getExternalOrderNo())
            .eq(StringUtils.isNotBlank(bo.getGatewayTradeNo()),
                PmExternalOrderBinding::getGatewayTradeNo, bo.getGatewayTradeNo())
            .eq(StringUtils.isNotBlank(bo.getRiskStatus()),
                PmExternalOrderBinding::getRiskStatus, bo.getRiskStatus())
            .orderByDesc(PmExternalOrderBinding::getCreatedAt);
        Page<PmExternalOrderBinding> page = bindingMapper.selectPage(pageQuery.build(), wrapper);
        List<ExternalOrderVo> rows = page.getRecords().stream().map(this::toExternalVo).toList();
        enrichExternalOrders(rows);
        return PageResult.build(rows, page.getTotal());
    }

    public ExternalOrderVo externalOrder(Long id) {
        ExternalOrderVo vo = toExternalVo(requireBinding(id));
        enrichExternalOrders(List.of(vo));
        return vo;
    }

    public PageResult<ProtocolCallbackVo> callbacks(ProtocolCallbackQueryBo bo, PageQuery pageQuery) {
        Long merchantId = MerchantContext.resolveQueryMerchantId(bo.getMerchantId());
        var wrapper = new LambdaQueryWrapper<PmProtocolCallbackOutbox>()
            .eq(merchantId != null, PmProtocolCallbackOutbox::getMerchantId, merchantId)
            .eq(bo.getIntegrationId() != null, PmProtocolCallbackOutbox::getIntegrationId, bo.getIntegrationId())
            .eq(StringUtils.isNotBlank(bo.getDeliveryId()),
                PmProtocolCallbackOutbox::getDeliveryId, bo.getDeliveryId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), PmProtocolCallbackOutbox::getStatus, bo.getStatus())
            .eq(bo.getBindingId() != null, PmProtocolCallbackOutbox::getBindingId, bo.getBindingId())
            .orderByDesc(PmProtocolCallbackOutbox::getCreatedAt);
        Page<PmProtocolCallbackOutbox> page = outboxMapper.selectPage(pageQuery.build(), wrapper);
        List<ProtocolCallbackVo> rows =
            page.getRecords().stream().map(item -> toCallbackVo(item, false)).toList();
        enrichCallbacks(rows);
        return PageResult.build(rows, page.getTotal());
    }

    public ProtocolCallbackVo callback(Long id) {
        ProtocolCallbackVo vo = toCallbackVo(requireOutbox(id), true);
        enrichCallbacks(List.of(vo));
        return vo;
    }

    public ProtocolCallbackVo retry(Long id) {
        PmProtocolCallbackOutbox outbox = requireOutbox(id);
        if (!"DEAD".equals(outbox.getStatus())) {
            throw new ServiceException("只有已停止重试的回调可以重新入队");
        }
        reset(outbox, now());
        outboxMapper.updateById(outbox);
        return callback(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProtocolCallbackVo replay(Long id, String reason) {
        PmProtocolCallbackOutbox original = requireOutbox(id);
        OffsetDateTime timestamp = now();
        PmProtocolCallbackOutbox replay = new PmProtocolCallbackOutbox();
        replay.setId(IdWorker.getId());
        replay.setDeliveryId(UUID.randomUUID().toString());
        replay.setEventId(original.getEventId());
        replay.setMerchantId(original.getMerchantId());
        replay.setIntegrationId(original.getIntegrationId());
        replay.setBindingId(original.getBindingId());
        replay.setCallbackKind(original.getCallbackKind());
        replay.setTargetUrl(original.getTargetUrl());
        replay.setRequestMethod(original.getRequestMethod());
        replay.setContentType(original.getContentType());
        replay.setCredentialVersion(original.getCredentialVersion());
        replay.setUnsignedParams(original.getUnsignedParams());
        replay.setStatus("PENDING");
        replay.setAttemptCount(0);
        replay.setNextAttemptAt(timestamp);
        replay.setStrictAcknowledged(false);
        replay.setReplayOfId(original.getId());
        replay.setReplayReason(reason.trim());
        replay.setCreatedAt(timestamp);
        replay.setUpdatedAt(timestamp);
        outboxMapper.insertOnConflict(replay);
        return toCallbackVo(replay, false);
    }

    private void reset(PmProtocolCallbackOutbox outbox, OffsetDateTime timestamp) {
        outbox.setStatus("PENDING"); outbox.setAttemptCount(0); outbox.setNextAttemptAt(timestamp);
        outbox.setLockedAt(null); outbox.setDeliveredAt(null); outbox.setLastHttpStatus(null);
        outbox.setLastResponse(null); outbox.setLastError(null); outbox.setStrictAcknowledged(false);
        outbox.setUpdatedAt(timestamp);
    }

    private PmExternalOrderBinding requireBinding(Long id) {
        PmExternalOrderBinding binding = bindingMapper.selectById(id);
        if (binding == null) throw new ServiceException("外部订单不存在");
        MerchantContext.requireAccessibleMerchant(binding.getMerchantId());
        return binding;
    }

    private PmProtocolCallbackOutbox requireOutbox(Long id) {
        PmProtocolCallbackOutbox outbox = outboxMapper.selectById(id);
        if (outbox == null) throw new ServiceException("易支付回调任务不存在");
        MerchantContext.requireAccessibleMerchant(outbox.getMerchantId());
        return outbox;
    }

    private ExternalOrderVo toExternalVo(PmExternalOrderBinding binding) {
        ExternalOrderVo vo = new ExternalOrderVo();
        vo.setId(binding.getId()); vo.setMerchantId(binding.getMerchantId());
        vo.setIntegrationId(binding.getIntegrationId());
        PmPaymentIntegration integration = integrationService.requireInternal(binding.getIntegrationId());
        vo.setIntegrationName(integration.getIntegrationName());
        vo.setOrderId(binding.getOrderId()); vo.setExternalOrderNo(binding.getExternalOrderNo());
        vo.setGatewayTradeNo(binding.getGatewayTradeNo()); vo.setPayType(binding.getPayType());
        vo.setRequestAmountMinor(binding.getRequestAmountMinor());
        vo.setCallbackPolicy(binding.getCallbackPolicy()); vo.setRiskStatus(binding.getRiskStatus());
        vo.setRiskReason(binding.getRiskReason()); vo.setCreatedAt(binding.getCreatedAt());
        PmPaymentOrder order = orderMapper.selectById(binding.getOrderId());
        if (order != null && order.getMerchantId().equals(binding.getMerchantId())) {
            vo.setInternalOrderNo(order.getMerchantOrderNo()); vo.setPlatform(order.getPlatform());
            vo.setPayableAmountMinor(order.getPayableAmountMinor()); vo.setOrderStatus(order.getStatus());
            vo.setConfirmationStatus(order.getConfirmationStatus()); vo.setPaidAt(order.getPaidAt());
        }
        PmProtocolCallbackOutbox callback = outboxMapper.selectOne(
            new LambdaQueryWrapper<PmProtocolCallbackOutbox>()
                .eq(PmProtocolCallbackOutbox::getBindingId, binding.getId())
                .orderByDesc(PmProtocolCallbackOutbox::getCreatedAt).last("limit 1"));
        vo.setCallbackStatus(callback == null ? null : callback.getStatus());
        return vo;
    }

    private ProtocolCallbackVo toCallbackVo(PmProtocolCallbackOutbox outbox, boolean logs) {
        ProtocolCallbackVo vo = new ProtocolCallbackVo();
        vo.setId(outbox.getId()); vo.setMerchantId(outbox.getMerchantId());
        vo.setDeliveryId(outbox.getDeliveryId()); vo.setEventId(outbox.getEventId());
        vo.setIntegrationId(outbox.getIntegrationId());
        vo.setIntegrationName(integrationService.requireInternal(outbox.getIntegrationId()).getIntegrationName());
        vo.setBindingId(outbox.getBindingId());
        PmExternalOrderBinding binding = bindingMapper.selectById(outbox.getBindingId());
        if (binding != null && binding.getMerchantId().equals(outbox.getMerchantId())) {
            vo.setExternalOrderNo(binding.getExternalOrderNo()); vo.setGatewayTradeNo(binding.getGatewayTradeNo());
        }
        vo.setRequestMethod(outbox.getRequestMethod()); vo.setTargetUrl(outbox.getTargetUrl());
        vo.setStatus(outbox.getStatus()); vo.setAttemptCount(outbox.getAttemptCount());
        vo.setNextAttemptAt(outbox.getNextAttemptAt()); vo.setDeliveredAt(outbox.getDeliveredAt());
        vo.setLastHttpStatus(outbox.getLastHttpStatus()); vo.setLastResponse(outbox.getLastResponse());
        vo.setLastError(outbox.getLastError()); vo.setStrictAcknowledged(outbox.getStrictAcknowledged());
        vo.setReplayOfId(outbox.getReplayOfId()); vo.setReplayReason(outbox.getReplayReason());
        vo.setCreatedAt(outbox.getCreatedAt());
        if (logs) {
            vo.setDeliveryLogs(deliveryLogMapper.selectList(
                new LambdaQueryWrapper<PmProtocolCallbackDeliveryLog>()
                    .eq(PmProtocolCallbackDeliveryLog::getOutboxId, outbox.getId())
                    .orderByDesc(PmProtocolCallbackDeliveryLog::getAttemptNumber)));
        }
        return vo;
    }

    private void enrichExternalOrders(List<ExternalOrderVo> rows) {
        merchantDisplayService.enrich(
            rows,
            ExternalOrderVo::getMerchantId,
            ExternalOrderVo::setMerchantCode,
            ExternalOrderVo::setMerchantName);
    }

    private void enrichCallbacks(List<ProtocolCallbackVo> rows) {
        merchantDisplayService.enrich(
            rows,
            ProtocolCallbackVo::getMerchantId,
            ProtocolCallbackVo::setMerchantCode,
            ProtocolCallbackVo::setMerchantName);
    }

    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
}
