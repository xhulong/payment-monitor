package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName("pm_reconciliation_item")
public class PmReconciliationItem implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long merchantId;
    @TableField(exist = false)
    private String merchantCode;
    @TableField(exist = false)
    private String merchantName;
    private Long runId;
    private String differenceType;
    private String status;
    private Long orderId;
    private Long eventId;
    private Long transactionId;
    private Long webhookOutboxId;
    private Long amountMinor;
    private String description;
    private String resolutionAction;
    private String resolutionNote;
    private Long resolvedBy;
    private OffsetDateTime resolvedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
