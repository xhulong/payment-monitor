package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName("pm_amount_slot_reservation")
public class PmAmountSlotReservation implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long merchantId;
    @TableField(exist = false)
    private String merchantCode;
    @TableField(exist = false)
    private String merchantName;
    private String platform;
    private Long payableAmountMinor;
    private Long orderId;
    private String status;
    private OffsetDateTime reservedAt;
    private OffsetDateTime coolingUntil;
    private OffsetDateTime releasedAt;
    private Integer version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
