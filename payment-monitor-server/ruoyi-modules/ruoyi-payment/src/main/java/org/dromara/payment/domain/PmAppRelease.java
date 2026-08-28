package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("pm_app_release")
public class PmAppRelease {
    @TableId
    private Long id;
    private String platform;
    private Integer versionCode;
    private String versionName;
    private Integer minSupportedVersionCode;
    private OffsetDateTime enforcementAt;
    private String objectKey;
    private Long fileSize;
    private String sha256;
    private String signingCertificateSha256;
    private String verifiedPackageName;
    private Integer verifiedVersionCode;
    private String verifiedVersionName;
    private String verificationStatus;
    private String updateMode;
    private String releaseNotes;
    private String status;
    private OffsetDateTime publishedAt;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
