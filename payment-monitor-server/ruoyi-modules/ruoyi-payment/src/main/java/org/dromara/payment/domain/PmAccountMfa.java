package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.payment.mybatis.JsonbStringTypeHandler;

import java.time.OffsetDateTime;

@Data
@TableName(value = "pm_account_mfa", autoResultMap = true)
public class PmAccountMfa {
    @TableId
    private Long id;
    private Long userId;
    private String totpSecretCiphertext;
    private String pendingSecretCiphertext;
    private OffsetDateTime pendingExpiresAt;
    private Boolean enabled;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String recoveryCodeHashes;
    private Long lastUsedTimeStep;
    private OffsetDateTime enabledAt;
    private OffsetDateTime lastUsedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
