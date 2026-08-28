package org.dromara.payment.integration.epay.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.payment.mybatis.JsonbStringTypeHandler;

import java.time.OffsetDateTime;

@Data
@TableName(value = "pm_external_order_binding", autoResultMap = true)
public class PmExternalOrderBinding {
    @TableId
    private Long id;
    private Long merchantId;
    private Long integrationId;
    private Long orderId;
    private String protocol;
    private String protocolProfile;
    private String externalOrderNo;
    private String gatewayTradeNo;
    private String payType;
    private Long requestAmountMinor;
    private String notifyUrl;
    private String returnUrl;
    private String passthroughParam;
    private Integer credentialVersion;
    private String notifyMethod;
    private String callbackPolicy;
    private String allowedCallbackHosts;
    private String requestFingerprint;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String requestSnapshot;
    private String riskStatus;
    private String riskReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
