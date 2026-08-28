package org.dromara.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.payment.domain.PmAmountSlotReservation;

import java.time.OffsetDateTime;

public interface AmountSlotMapper extends BaseMapper<PmAmountSlotReservation> {
    @Select("""
        select * from pm_amount_slot_reservation
        where merchant_id = #{merchantId}
          and platform = #{platform}
          and payable_amount_minor = #{amountMinor}
        for update
        """)
    PmAmountSlotReservation selectKeyForUpdate(
        @Param("merchantId") Long merchantId,
        @Param("platform") String platform,
        @Param("amountMinor") Long amountMinor
    );

    @Update("""
        update pm_amount_slot_reservation
        set status = 'COOLING',
            cooling_until = #{coolingUntil},
            released_at = null,
            updated_at = #{now},
            version = version + 1
        where order_id = #{orderId}
          and status in ('ACTIVE', 'COOLING', 'RELEASED')
        """)
    int startCooling(
        @Param("orderId") Long orderId,
        @Param("coolingUntil") OffsetDateTime coolingUntil,
        @Param("now") OffsetDateTime now
    );

    @Update("""
        update pm_amount_slot_reservation
        set status = 'ACTIVE',
            cooling_until = null,
            released_at = null,
            updated_at = #{now},
            version = version + 1
        where order_id = #{orderId}
          and status in ('COOLING', 'RELEASED')
        """)
    int reactivate(
        @Param("orderId") Long orderId,
        @Param("now") OffsetDateTime now
    );

    @Update("""
        update pm_amount_slot_reservation
        set status = 'RELEASED',
            released_at = #{now},
            updated_at = #{now},
            version = version + 1
        where status = 'COOLING'
          and cooling_until <= #{now}
        """)
    int releaseExpired(@Param("now") OffsetDateTime now);
}
