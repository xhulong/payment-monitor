package org.dromara.payment.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.payment.domain.PmWebhookOutbox;
import org.dromara.payment.domain.vo.WebhookOutboxVo;

import java.time.OffsetDateTime;
import java.util.List;

public interface WebhookOutboxMapper extends BaseMapperPlus<PmWebhookOutbox, WebhookOutboxVo> {
    int insertOnConflict(PmWebhookOutbox outbox);

    List<PmWebhookOutbox> claimDue(
        @Param("now") OffsetDateTime now,
        @Param("lockCutoff") OffsetDateTime lockCutoff,
        @Param("limit") int limit
    );
}
