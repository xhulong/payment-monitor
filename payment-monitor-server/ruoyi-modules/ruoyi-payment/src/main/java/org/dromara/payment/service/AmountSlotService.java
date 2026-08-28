package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmAmountSlotReservation;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.bo.AmountSlotQueryBo;
import org.dromara.payment.mapper.AmountSlotMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AmountSlotService {
    public static final long COOLING_SECONDS = 600;

    private final AmountSlotMapper mapper;
    private final MerchantDisplayService merchantDisplayService;

    @Transactional(rollbackFor = Exception.class)
    public boolean reserve(PmPaymentOrder order) {
        OffsetDateTime timestamp = now();
        mapper.releaseExpired(timestamp);
        PmAmountSlotReservation existing = mapper.selectKeyForUpdate(
            order.getMerchantId(),
            order.getPlatform(),
            order.getPayableAmountMinor());
        if (existing == null) {
            PmAmountSlotReservation slot = new PmAmountSlotReservation();
            slot.setId(IdWorker.getId());
            slot.setMerchantId(order.getMerchantId());
            slot.setPlatform(order.getPlatform());
            slot.setPayableAmountMinor(order.getPayableAmountMinor());
            slot.setOrderId(order.getId());
            slot.setStatus(PaymentConstants.SLOT_ACTIVE);
            slot.setReservedAt(timestamp);
            slot.setVersion(0);
            slot.setCreatedAt(timestamp);
            slot.setUpdatedAt(timestamp);
            mapper.insert(slot);
            return true;
        }
        boolean reusable = PaymentConstants.SLOT_RELEASED.equals(existing.getStatus())
            || (PaymentConstants.SLOT_COOLING.equals(existing.getStatus())
                && existing.getCoolingUntil() != null
                && !existing.getCoolingUntil().isAfter(timestamp));
        if (!reusable) {
            return existing.getOrderId().equals(order.getId());
        }
        existing.setOrderId(order.getId());
        existing.setStatus(PaymentConstants.SLOT_ACTIVE);
        existing.setReservedAt(timestamp);
        existing.setCoolingUntil(null);
        existing.setReleasedAt(null);
        existing.setUpdatedAt(timestamp);
        existing.setVersion(existing.getVersion() == null ? 1 : existing.getVersion() + 1);
        mapper.updateById(existing);
        return true;
    }

    public void startCooling(Long orderId) {
        OffsetDateTime timestamp = now();
        mapper.startCooling(orderId, timestamp.plusSeconds(COOLING_SECONDS), timestamp);
    }

    public void reactivate(Long orderId) {
        mapper.reactivate(orderId, now());
    }

    public PageResult<PmAmountSlotReservation> queryPage(
        AmountSlotQueryBo bo,
        PageQuery pageQuery
    ) {
        mapper.releaseExpired(now());
        Long merchantId = MerchantContext.resolveQueryMerchantId(bo.getMerchantId());
        LambdaQueryWrapper<PmAmountSlotReservation> wrapper =
            new LambdaQueryWrapper<PmAmountSlotReservation>()
                .eq(merchantId != null, PmAmountSlotReservation::getMerchantId, merchantId)
                .eq(bo.getPlatform() != null, PmAmountSlotReservation::getPlatform, bo.getPlatform())
                .eq(bo.getStatus() != null, PmAmountSlotReservation::getStatus, bo.getStatus())
                .eq(bo.getPayableAmountMinor() != null,
                    PmAmountSlotReservation::getPayableAmountMinor,
                    bo.getPayableAmountMinor())
                .eq(bo.getOrderId() != null, PmAmountSlotReservation::getOrderId, bo.getOrderId())
                .orderByDesc(PmAmountSlotReservation::getUpdatedAt);
        Page<PmAmountSlotReservation> page = mapper.selectPage(pageQuery.build(), wrapper);
        merchantDisplayService.enrich(
            page.getRecords(),
            PmAmountSlotReservation::getMerchantId,
            PmAmountSlotReservation::setMerchantCode,
            PmAmountSlotReservation::setMerchantName);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    public PmAmountSlotReservation findByOrder(Long merchantId, Long orderId) {
        return mapper.selectOne(new LambdaQueryWrapper<PmAmountSlotReservation>()
            .eq(PmAmountSlotReservation::getMerchantId, merchantId)
            .eq(PmAmountSlotReservation::getOrderId, orderId)
            .last("limit 1"));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
