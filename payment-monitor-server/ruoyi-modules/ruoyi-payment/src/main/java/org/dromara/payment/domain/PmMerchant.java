package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.payment.mybatis.JsonbStringTypeHandler;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 支付商户。
 */
@Data
@TableName(value = "pm_merchant", autoResultMap = true)
public class PmMerchant implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private String merchantCode;
    private String name;
    private String status;
    private String lifecycleStatus;
    private Long ownerUserId;
    private String timezone;
    private String remark;
    private String agreementVersion;
    private String privacyVersion;
    private OffsetDateTime onboardingCompletedAt;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String quotaConfig;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
