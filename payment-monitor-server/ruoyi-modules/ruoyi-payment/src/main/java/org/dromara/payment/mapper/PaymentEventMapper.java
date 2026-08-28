package org.dromara.payment.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.vo.PaymentEventVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 支付事件 Mapper。
 */
public interface PaymentEventMapper extends BaseMapperPlus<PmPaymentEvent, PaymentEventVo> {

    /**
     * 按业务唯一键插入事件，冲突时忽略。
     *
     * @param event 支付事件
     * @return 插入行数
     */
    int insertOnConflict(PmPaymentEvent event);

    PmPaymentEvent selectByIdForUpdate(
        @Param("id") Long id,
        @Param("merchantId") Long merchantId
    );

    List<PmPaymentEvent> selectManualMatchCandidates(
        @Param("merchantId") Long merchantId,
        @Param("orderId") Long orderId,
        @Param("platform") String platform,
        @Param("amountMinor") Long amountMinor,
        @Param("limit") int limit
    );

    int countManualMatchOccupations(
        @Param("merchantId") Long merchantId,
        @Param("orderId") Long orderId,
        @Param("eventId") Long eventId
    );

    default PmPaymentEvent selectByIdForUpdate(Long id) {
        return selectByIdForUpdate(id, org.dromara.payment.constant.PaymentConstants.DEFAULT_MERCHANT_ID);
    }
}
