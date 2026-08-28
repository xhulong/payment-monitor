package org.dromara.payment.integration.epay.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("pm_payment_integration_secret")
public class PmPaymentIntegrationSecret {
    @TableId
    private Long id;
    private Long integrationId;
    private Integer secretVersion;
    private String secretCiphertext;
    private String encryptionKeyId;
    private String status;
    private OffsetDateTime activatedAt;
    private OffsetDateTime retiredAt;
    private OffsetDateTime revokedAt;
    private OffsetDateTime createdAt;
}
