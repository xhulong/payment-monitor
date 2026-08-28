package org.dromara.payment.integration.epay.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.payment.integration.epay.domain.PmProtocolCallbackOutbox;

import java.time.OffsetDateTime;
import java.util.List;

public interface ProtocolCallbackOutboxMapper extends BaseMapperPlus<PmProtocolCallbackOutbox, PmProtocolCallbackOutbox> {
    int insertOnConflict(PmProtocolCallbackOutbox outbox);
    List<PmProtocolCallbackOutbox> claimDue(@Param("now") OffsetDateTime now,
                                            @Param("lockCutoff") OffsetDateTime lockCutoff,
                                            @Param("limit") int limit);
}
