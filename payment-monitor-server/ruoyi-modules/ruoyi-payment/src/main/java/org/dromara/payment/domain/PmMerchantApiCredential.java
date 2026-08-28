package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName("pm_merchant_api_credential")
public class PmMerchantApiCredential implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long apiKeyId;
    private Integer credentialVersion;
    private String secretCiphertext;
    private OffsetDateTime createdAt;
    private OffsetDateTime revokedAt;
}
