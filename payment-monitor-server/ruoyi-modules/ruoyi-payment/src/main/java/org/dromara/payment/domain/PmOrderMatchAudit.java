package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName("pm_order_match_audit")
public class PmOrderMatchAudit implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long merchantId;
    private Long orderId;
    private Long eventId;
    private String action;
    private String beforeStatus;
    private String afterStatus;
    private String note;
    private Long operatedBy;
    private OffsetDateTime operatedAt;
}
