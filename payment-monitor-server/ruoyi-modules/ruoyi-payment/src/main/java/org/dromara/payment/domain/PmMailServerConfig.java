package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("pm_mail_server_config")
public class PmMailServerConfig {
    @TableId
    private Long id;
    private Boolean enabled;
    private String host;
    private Integer port;
    private Boolean authEnabled;
    private String username;
    private String passwordCiphertext;
    private String encryptionKeyId;
    private String fromName;
    private String fromAddress;
    private String securityMode;
    private Long connectionTimeoutMs;
    private Long readTimeoutMs;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
    private Integer version;
}
