package org.dromara.payment.integration.epay.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("pm_payment_integration")
public class PmPaymentIntegration {
    @TableId
    private Long id;
    private Long merchantId;
    private String integrationCode;
    private String integrationName;
    private String protocol;
    private String profile;
    private String pid;
    private String status;
    private Integer defaultExpireSeconds;
    private String notifyMethod;
    private String callbackPolicy;
    private String allowedCallbackHosts;
    private String remark;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
