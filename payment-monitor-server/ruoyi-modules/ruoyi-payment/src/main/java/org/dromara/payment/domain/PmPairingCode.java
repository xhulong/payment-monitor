package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 一次性设备配对码。
 */
@Data
@TableName("pm_pairing_code")
public class PmPairingCode implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long merchantId;
    private String codeHash;
    private OffsetDateTime expiresAt;
    private OffsetDateTime usedAt;
    private Long usedByDeviceId;
    private Long createdBy;
    private OffsetDateTime createdAt;
}
