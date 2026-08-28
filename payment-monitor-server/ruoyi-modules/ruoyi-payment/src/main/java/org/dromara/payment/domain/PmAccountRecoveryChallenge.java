package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("pm_account_recovery_challenge")
public class PmAccountRecoveryChallenge {
    @TableId
    private Long id;
    private String challengeId;
    private String challengeType;
    private Long userId;
    private String targetEmail;
    private String codeHash;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private OffsetDateTime expiresAt;
    private OffsetDateTime lastAttemptAt;
    private OffsetDateTime resolvedAt;
    private String resolutionReason;
    private String createdIp;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
