package org.dromara.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("pm_refresh_session")
public class PmRefreshSession {
    @TableId
    private Long id;
    private String sessionId;
    private String familyId;
    private Long rotatedFromId;
    private Long userId;
    private String loginId;
    private String clientId;
    private String tokenHash;
    private String status;
    private OffsetDateTime issuedAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime lastUsedAt;
    private OffsetDateTime revokedAt;
    private String revokeReason;
    private String createdIp;
    private String lastUsedIp;
    private String userAgentHash;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
