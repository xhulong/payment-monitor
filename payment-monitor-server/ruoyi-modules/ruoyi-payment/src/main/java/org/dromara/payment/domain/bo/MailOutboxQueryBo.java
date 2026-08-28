package org.dromara.payment.domain.bo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class MailOutboxQueryBo {
    private String status;
    private String messageType;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
}
