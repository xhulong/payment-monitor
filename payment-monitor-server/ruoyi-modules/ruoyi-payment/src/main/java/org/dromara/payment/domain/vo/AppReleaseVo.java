package org.dromara.payment.domain.vo;

import java.time.OffsetDateTime;

public record AppReleaseVo(
    Long id,
    String platform,
    Integer versionCode,
    String versionName,
    Integer minSupportedVersionCode,
    OffsetDateTime enforcementAt,
    String downloadUrl,
    Long fileSize,
    String sha256,
    String signingCertificateSha256,
    String verifiedPackageName,
    String verificationStatus,
    String updateMode,
    String releaseNotes,
    String status,
    OffsetDateTime publishedAt
) {
}
