package org.dromara.payment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.payment.domain.PmOrderMatchAudit;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = PmOrderMatchAudit.class)
public class OrderMatchAuditVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long eventId;
    private String action;
    private String beforeStatus;
    private String afterStatus;
    private String note;
    private Long operatedBy;
    private OffsetDateTime operatedAt;
}
