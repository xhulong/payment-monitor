package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("pm_merchant_invitation")
public class PmMerchantInvitation {
    @TableId
    private Long id;
    private Long merchantId;
    private String invitedEmail;
    private String roleCode;
    private String tokenHash;
    private String status;
    private Long invitedBy;
    private Long acceptedBy;
    private OffsetDateTime expiresAt;
    private OffsetDateTime acceptedAt;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
