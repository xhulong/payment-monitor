package org.dromara.payment.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class MerchantVo {
    private Long id;
    private String merchantCode;
    private String name;
    private String status;
    private String lifecycleStatus;
    private String timezone;
    private String remark;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long adminUserId;
    private String adminUserName;
}
