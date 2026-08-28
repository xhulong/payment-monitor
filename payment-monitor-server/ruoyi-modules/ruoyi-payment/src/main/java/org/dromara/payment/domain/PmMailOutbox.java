package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("pm_mail_outbox")
public class PmMailOutbox {
    @TableId
    private Long id;
    private String messageId;
    private String messageType;
    private String deduplicationKey;
    private String payloadCiphertext;
    private String encryptionKeyId;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private OffsetDateTime nextAttemptAt;
    private OffsetDateTime lockedAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime sentAt;
    private String lastError;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
