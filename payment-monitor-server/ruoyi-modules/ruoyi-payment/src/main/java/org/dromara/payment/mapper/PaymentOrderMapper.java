package org.dromara.payment.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.vo.PaymentOrderVo;

import java.time.OffsetDateTime;
import java.util.List;

public interface PaymentOrderMapper extends BaseMapperPlus<PmPaymentOrder, PaymentOrderVo> {
    int insertOnConflict(PmPaymentOrder order);

    List<PmPaymentOrder> selectMatchCandidatesForUpdate(
        @Param("merchantId") Long merchantId,
        @Param("platform") String platform,
        @Param("amountMinor") Long amountMinor,
        @Param("eventTime") OffsetDateTime eventTime
    );

    PmPaymentOrder selectByIdForUpdate(
        @Param("id") Long id,
        @Param("merchantId") Long merchantId
    );

    default PmPaymentOrder selectByIdForUpdate(Long id) {
        return selectByIdForUpdate(id, org.dromara.payment.constant.PaymentConstants.DEFAULT_MERCHANT_ID);
    }
}
