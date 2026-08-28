package org.dromara.payment.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class MerchantApiKeyVo {
    private Long id;
    private Long merchantId;
    private String keyId;
    private String keyName;
    private String status;
    private Integer currentVersion;
    private OffsetDateTime lastUsedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
