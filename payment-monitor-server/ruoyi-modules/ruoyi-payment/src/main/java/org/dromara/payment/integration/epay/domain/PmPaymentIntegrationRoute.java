package org.dromara.payment.integration.epay.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("pm_payment_integration_route")
public class PmPaymentIntegrationRoute {
    @TableId
    private Long id;
    private Long integrationId;
    private Long merchantId;
    private String payType;
    private String platform;
    private Long qrAssetId;
    private Integer priority;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
