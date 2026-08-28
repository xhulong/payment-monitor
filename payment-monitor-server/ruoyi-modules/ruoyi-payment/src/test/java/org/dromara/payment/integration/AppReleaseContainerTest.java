package org.dromara.payment.integration;

import org.apache.ibatis.session.SqlSession;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.PmAppRelease;
import org.dromara.payment.domain.dto.AppReleaseSaveRequest;
import org.dromara.payment.domain.dto.AppReleaseUpdateRequest;
import org.dromara.payment.mapper.AppReleaseMapper;
import org.dromara.payment.service.ApkInspectionService;
import org.dromara.payment.service.AppReleaseService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class AppReleaseContainerTest {

    private static final String CERT_SHA256 = "cd".repeat(32);
    private static final String APK_SHA256 = "ab".repeat(32);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("payment_monitor")
            .withUsername("payment_monitor")
            .withPassword("payment_monitor_test");

    @Test
    void validatesUploadsPublishesAndRejectsDowngrades() throws Exception {
        DataSource dataSource =
            PaymentPostgresTestSupport.migrateLatest(POSTGRES, "app_releases");
        var sessionFactory = PaymentPostgresTestSupport.sqlSessionFactory(dataSource);

        try (SqlSession session = sessionFactory.openSession(true)) {
            AppReleaseMapper mapper = session.getMapper(AppReleaseMapper.class);
            PaymentProperties properties = new PaymentProperties();
            properties.setPublicBaseUrl("https://payment.example.test");
            properties.getAppRelease().setExpectedPackageName(
                "com.xhulong.paymentmonitor"
            );
            properties.getAppRelease().setExpectedSigningCertificateSha256(
                CERT_SHA256
            );
            properties.getAppRelease().setDownloadSigningSecret(
                "fixture-download-signing-secret-32-bytes"
            );

            S3Client s3Client = mock(S3Client.class);
            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());
            when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
            )).thenReturn(PutObjectResponse.builder().build());

            ApkInspectionService inspector = mock(ApkInspectionService.class);
            when(inspector.inspect(any())).thenReturn(inspection(
                8,
                "1.7.0-rc1",
                "com.xhulong.paymentmonitor",
                CERT_SHA256
            ));
            AppReleaseService service = new AppReleaseService(
                mapper,
                properties,
                s3Client,
                inspector
            );

            try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
                login.when(LoginHelper::getUserId).thenReturn(8001L);
                var draft = service.upload(
                    releaseRequest(8, "1.7.0-rc1"),
                    apk("payment-monitor-1.7.0-rc1.apk")
                );
                assertEquals("DRAFT", draft.status());
                assertEquals("VERIFIED", draft.verificationStatus());
                assertEquals(APK_SHA256, draft.sha256());
                assertEquals(CERT_SHA256, draft.signingCertificateSha256());
                assertEquals("com.xhulong.paymentmonitor", draft.verifiedPackageName());

                var published = service.publish(draft.id());
                assertEquals("PUBLISHED", published.status());
                assertNotNull(published.publishedAt());
                assertNotNull(published.enforcementAt());
                assertTrue(published.downloadUrl().startsWith(
                    "https://payment.example.test/api/v1/public/app-releases/"
                ));

                AppReleaseUpdateRequest updateRequest = new AppReleaseUpdateRequest();
                updateRequest.setMinSupportedVersionCode(7);
                updateRequest.setUpdateMode("OPTIONAL");
                updateRequest.setReleaseNotes("修复更新说明显示并优化自动更新");
                var updated = service.update(draft.id(), updateRequest);
                assertEquals(7, updated.minSupportedVersionCode());
                assertEquals("OPTIONAL", updated.updateMode());
                assertEquals(
                    "修复更新说明显示并优化自动更新",
                    updated.releaseNotes()
                );
            }

            PmAppRelease stored = mapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PmAppRelease>()
                    .eq(PmAppRelease::getVersionCode, 8)
                    .last("limit 1")
            );
            assertEquals("PUBLISHED", stored.getStatus());
            assertEquals(8, stored.getVerifiedVersionCode());
            assertEquals("1.7.0-rc1", stored.getVerifiedVersionName());
            assertEquals(7, stored.getMinSupportedVersionCode());
            assertEquals("OPTIONAL", stored.getUpdateMode());
            assertEquals("修复更新说明显示并优化自动更新", stored.getReleaseNotes());

            when(inspector.inspect(any())).thenReturn(inspection(
                7,
                "1.6.9",
                "com.xhulong.paymentmonitor",
                CERT_SHA256
            ));
            assertThrows(
                ServiceException.class,
                () -> service.upload(
                    releaseRequest(7, "1.6.9"),
                    apk("payment-monitor-1.6.9.apk")
                )
            );

            when(inspector.inspect(any())).thenReturn(inspection(
                9,
                "1.7.0",
                "com.example.wrong",
                CERT_SHA256
            ));
            assertThrows(
                ServiceException.class,
                () -> service.upload(
                    releaseRequest(9, "1.7.0"),
                    apk("payment-monitor-wrong-package.apk")
                )
            );

            when(inspector.inspect(any())).thenReturn(inspection(
                9,
                "1.7.0",
                "com.xhulong.paymentmonitor",
                "ef".repeat(32)
            ));
            assertThrows(
                ServiceException.class,
                () -> service.upload(
                    releaseRequest(9, "1.7.0"),
                    apk("payment-monitor-wrong-signature.apk")
                )
            );
            assertThrows(
                ServiceException.class,
                () -> service.upload(
                    releaseRequest(9, "1.7.0"),
                    new MockMultipartFile(
                        "apk",
                        "not-an-apk.txt",
                        "text/plain",
                        "fixture".getBytes(StandardCharsets.UTF_8)
                    )
                )
            );

            assertEquals(1, mapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>()
            ));
            verify(s3Client, times(1)).putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
            );
        }
    }

    private ApkInspectionService.ApkInspection inspection(
        int versionCode,
        String versionName,
        String packageName,
        String certificate
    ) {
        return new ApkInspectionService.ApkInspection(
            packageName,
            versionCode,
            versionName,
            certificate,
            APK_SHA256
        );
    }

    private AppReleaseSaveRequest releaseRequest(
        int versionCode,
        String versionName
    ) {
        AppReleaseSaveRequest request = new AppReleaseSaveRequest();
        request.setVersionCode(versionCode);
        request.setVersionName(versionName);
        request.setMinSupportedVersionCode(versionCode);
        request.setUpdateMode("SECURITY_BLOCK");
        request.setReleaseNotes("Phase L fixture release");
        return request;
    }

    private MockMultipartFile apk(String filename) {
        return new MockMultipartFile(
            "apk",
            filename,
            "application/vnd.android.package-archive",
            "desensitized signed apk fixture".getBytes(StandardCharsets.UTF_8)
        );
    }
}
