package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 设备密钥。
 */
@Data
@TableName("pm_device_credential")
public class PmDeviceCredential implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long deviceId;
    private String secretCiphertext;
    private Integer keyVersion;
    private OffsetDateTime createdAt;
    private OffsetDateTime revokedAt;
}
