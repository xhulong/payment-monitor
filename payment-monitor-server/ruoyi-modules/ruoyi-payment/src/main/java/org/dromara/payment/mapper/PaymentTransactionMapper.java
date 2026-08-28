package org.dromara.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.payment.domain.PmPaymentTransaction;

public interface PaymentTransactionMapper extends BaseMapper<PmPaymentTransaction> {
    @Insert("""
        insert into pm_payment_transaction (
            id, merchant_id, event_id, order_id, platform, amount_minor, currency,
            status, confirmation_status, observed_at, matched_at, confirmed_at,
            confirmed_by, reconciled_at, reversed_at, reversed_by, rejection_reason,
            version, created_at, updated_at
        ) values (
            #{id}, #{merchantId}, #{eventId}, #{orderId}, #{platform}, #{amountMinor}, #{currency},
            #{status}, #{confirmationStatus}, #{observedAt}, #{matchedAt}, #{confirmedAt},
            #{confirmedBy}, #{reconciledAt}, #{reversedAt}, #{reversedBy}, #{rejectionReason},
            #{version}, #{createdAt}, #{updatedAt}
        )
        on conflict (event_id) do nothing
        """)
    int insertOnConflict(PmPaymentTransaction transaction);

    @Select("""
        select * from pm_payment_transaction
        where id = #{id} and merchant_id = #{merchantId}
        for update
        """)
    PmPaymentTransaction selectByIdForUpdate(
        @Param("id") Long id,
        @Param("merchantId") Long merchantId
    );

    @Select("""
        select * from pm_payment_transaction
        where event_id = #{eventId} and merchant_id = #{merchantId}
        for update
        """)
    PmPaymentTransaction selectByEventForUpdate(
        @Param("eventId") Long eventId,
        @Param("merchantId") Long merchantId
    );
}
