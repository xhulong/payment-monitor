package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("pm_merchant_api_audit")
public class PmMerchantApiAudit {
    @TableId
    private Long id;
    private Long merchantId;
    @TableField(exist = false)
    private String merchantCode;
    @TableField(exist = false)
    private String merchantName;
    private Long apiKeyId;
    private String keyId;
    private String requestMethod;
    private String requestPath;
    private String clientIp;
    private Integer httpStatus;
    private String resultCode;
    private Boolean success;
    private Long durationMs;
    private OffsetDateTime createdAt;
}
