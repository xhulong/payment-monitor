package org.dromara.payment.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.PmAppRelease;
import org.dromara.payment.domain.dto.AppReleaseUpdateRequest;
import org.dromara.payment.mapper.AppReleaseMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class AppReleaseServiceTest {

    @Test
    void updatesReleasePolicyAndNotesWithoutChangingApkIdentity() {
        AppReleaseMapper mapper = mock(AppReleaseMapper.class);
        PmAppRelease release = release(13);
        when(mapper.selectById(31L)).thenReturn(release);
        AppReleaseService service = service(mapper);
        AppReleaseUpdateRequest request = updateRequest(12, "OPTIONAL");
        request.setReleaseNotes("修复更新说明显示");

        var updated = service.update(31L, request);

        assertEquals(13, updated.versionCode());
        assertEquals("1.0.1", updated.versionName());
        assertEquals(12, updated.minSupportedVersionCode());
        assertEquals("OPTIONAL", updated.updateMode());
        assertEquals("修复更新说明显示", updated.releaseNotes());
        assertEquals("ab".repeat(32), updated.sha256());
        assertEquals("cd".repeat(32), updated.signingCertificateSha256());
        verify(mapper).updateById(release);
    }

    @Test
    void rejectsMinimumVersionAboveReleaseVersion() {
        AppReleaseMapper mapper = mock(AppReleaseMapper.class);
        when(mapper.selectById(31L)).thenReturn(release(13));
        AppReleaseService service = service(mapper);

        ServiceException exception = assertThrows(
            ServiceException.class,
            () -> service.update(31L, updateRequest(14, "REQUIRED"))
        );

        assertEquals(
            "最低支持 versionCode 不能高于当前版本",
            exception.getMessage()
        );
    }

    private AppReleaseService service(AppReleaseMapper mapper) {
        return new AppReleaseService(
            mapper,
            new PaymentProperties(),
            mock(S3Client.class),
            mock(ApkInspectionService.class)
        );
    }

    private PmAppRelease release(int versionCode) {
        PmAppRelease release = new PmAppRelease();
        release.setId(31L);
        release.setPlatform("ANDROID");
        release.setVersionCode(versionCode);
        release.setVersionName("1.0.1");
        release.setMinSupportedVersionCode(9);
        release.setObjectKey("android/13-1.0.1.apk");
        release.setFileSize(1024L);
        release.setSha256("ab".repeat(32));
        release.setSigningCertificateSha256("cd".repeat(32));
        release.setVerifiedPackageName("com.xhulong.paymentmonitor");
        release.setVerificationStatus("VERIFIED");
        release.setUpdateMode("OPTIONAL");
        release.setStatus("DRAFT");
        return release;
    }

    private AppReleaseUpdateRequest updateRequest(
        int minSupportedVersionCode,
        String updateMode
    ) {
        AppReleaseUpdateRequest request = new AppReleaseUpdateRequest();
        request.setMinSupportedVersionCode(minSupportedVersionCode);
        request.setUpdateMode(updateMode);
        return request;
    }
}
