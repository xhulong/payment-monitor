package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName("pm_payment_transaction")
public class PmPaymentTransaction implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long merchantId;
    @TableField(exist = false)
    private String merchantCode;
    @TableField(exist = false)
    private String merchantName;
    private Long eventId;
    private Long orderId;
    private String platform;
    private Long amountMinor;
    private String currency;
    private String status;
    private String confirmationStatus;
    private OffsetDateTime observedAt;
    private OffsetDateTime matchedAt;
    private OffsetDateTime confirmedAt;
    private Long confirmedBy;
    private OffsetDateTime reconciledAt;
    private OffsetDateTime reversedAt;
    private Long reversedBy;
    private String rejectionReason;
    private Integer version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
