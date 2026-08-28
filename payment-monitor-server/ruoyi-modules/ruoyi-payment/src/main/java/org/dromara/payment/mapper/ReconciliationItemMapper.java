package org.dromara.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.payment.domain.PmReconciliationItem;

public interface ReconciliationItemMapper extends BaseMapper<PmReconciliationItem> {
    @Select("""
        select * from pm_reconciliation_item
        where id = #{id} and merchant_id = #{merchantId}
        for update
        """)
    PmReconciliationItem selectByIdForUpdate(
        @Param("id") Long id,
        @Param("merchantId") Long merchantId
    );
}
