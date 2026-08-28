package org.dromara.payment.integration.epay.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.payment.integration.epay.domain.PmExternalOrderBinding;

public interface ExternalOrderBindingMapper extends BaseMapperPlus<PmExternalOrderBinding, PmExternalOrderBinding> {
    int insertOnConflict(PmExternalOrderBinding binding);

    @Select("""
        select 1
        from pg_advisory_xact_lock(
            hashtextextended(concat(#{integrationId}, ':', #{externalOrderNo}), 0)
        )
        """)
    Integer lockExternalOrder(@Param("integrationId") Long integrationId,
                              @Param("externalOrderNo") String externalOrderNo);
}
