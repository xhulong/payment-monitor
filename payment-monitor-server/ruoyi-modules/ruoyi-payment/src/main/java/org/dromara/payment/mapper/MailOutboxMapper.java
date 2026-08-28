package org.dromara.payment.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.payment.domain.PmMailOutbox;

import java.time.OffsetDateTime;
import java.util.List;

public interface MailOutboxMapper
    extends BaseMapperPlus<PmMailOutbox, PmMailOutbox> {

    List<PmMailOutbox> claimDue(
        @Param("now") OffsetDateTime now,
        @Param("lockCutoff") OffsetDateTime lockCutoff,
        @Param("limit") int limit
    );

    @Select("""
        select count(*)
        from pm_mail_outbox
        where status in ('PENDING', 'SENDING', 'RETRYING')
        """)
    long backlogCount();

    @Select("""
        select count(*)
        from pm_mail_outbox
        where status = 'DEAD'
        """)
    long deadCount();
}
