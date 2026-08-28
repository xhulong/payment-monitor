package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.PmAppRelease;
import org.dromara.payment.domain.PmDevice;
import org.dromara.payment.domain.dto.AppReleaseSaveRequest;
import org.dromara.payment.domain.dto.AppReleaseUpdateRequest;
import org.dromara.payment.domain.vo.AppReleaseVo;
import org.dromara.payment.domain.vo.DeviceConfigVo;
import org.dromara.payment.mapper.AppReleaseMapper;
import org.dromara.payment.security.PaymentCrypto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppReleaseService {
    private final AppReleaseMapper releaseMapper;
    private final PaymentProperties properties;
    private final S3Client s3Client;
    private final ApkInspectionService apkInspectionService;

    public PageResult<AppReleaseVo> list(PageQuery pageQuery) {
        var page = releaseMapper.selectPage(pageQuery.build(),
            new LambdaQueryWrapper<PmAppRelease>()
                .eq(PmAppRelease::getPlatform, "ANDROID")
                .orderByDesc(PmAppRelease::getVersionCode));
        return PageResult.build(page.getRecords().stream().map(this::toVo).toList(), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public AppReleaseVo upload(AppReleaseSaveRequest request, MultipartFile apk) {
        if (apk == null || apk.isEmpty() || !apk.getOriginalFilename().toLowerCase().endsWith(".apk")) {
            throw new ServiceException("请上传 APK 文件");
        }
        if (releaseMapper.selectCount(new LambdaQueryWrapper<PmAppRelease>()
            .eq(PmAppRelease::getPlatform, "ANDROID")
            .eq(PmAppRelease::getVersionCode, request.getVersionCode())) > 0) {
            throw new ServiceException("该 versionCode 已存在");
        }
        Path temporaryFile = null;
        String uploadedObjectKey = null;
        try {
            temporaryFile = Files.createTempFile("payment-monitor-release-", ".apk");
            apk.transferTo(temporaryFile);
            ApkInspectionService.ApkInspection inspection =
                apkInspectionService.inspect(temporaryFile.toFile());
            validateInspection(request, inspection);
            rejectDowngrade(request.getVersionCode());
            String key = "android/" + request.getVersionCode() + "-" + request.getVersionName() + ".apk";
            uploadedObjectKey = key;
            ensureBucket();
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(properties.getAppRelease().getBucket())
                    .key(key)
                    .contentType("application/vnd.android.package-archive")
                    .build(),
                RequestBody.fromFile(temporaryFile));
            OffsetDateTime now = now();
            PmAppRelease release = new PmAppRelease();
            release.setId(IdWorker.getId());
            release.setPlatform("ANDROID");
            release.setVersionCode(request.getVersionCode());
            release.setVersionName(request.getVersionName());
            release.setMinSupportedVersionCode(request.getMinSupportedVersionCode());
            release.setEnforcementAt(request.getEnforcementAt());
            release.setObjectKey(key);
            release.setFileSize(Files.size(temporaryFile));
            release.setSha256(inspection.sha256());
            release.setSigningCertificateSha256(inspection.signingCertificateSha256());
            release.setVerifiedPackageName(inspection.packageName());
            release.setVerifiedVersionCode(inspection.versionCode());
            release.setVerifiedVersionName(inspection.versionName());
            release.setVerificationStatus("VERIFIED");
            release.setUpdateMode(request.getUpdateMode());
            release.setReleaseNotes(request.getReleaseNotes());
            release.setStatus("DRAFT");
            release.setCreatedBy(LoginHelper.getUserId());
            release.setCreatedAt(now);
            release.setUpdatedAt(now);
            releaseMapper.insert(release);
            return toVo(release);
        } catch (Exception exception) {
            if (uploadedObjectKey != null) {
                deleteObjectQuietly(uploadedObjectKey);
            }
            if (exception instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException("APK 上传失败：" + exception.getMessage());
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (Exception ignored) {
                    // Temporary upload cleanup must not mask the API result.
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AppReleaseVo publish(Long id) {
        PmAppRelease release = releaseMapper.selectById(id);
        if (release == null) {
            throw new ServiceException("App 版本不存在");
        }
        if (!"VERIFIED".equals(release.getVerificationStatus())) {
            throw new ServiceException("App 版本尚未通过 APK 签名与清单校验");
        }
        release.setStatus("PUBLISHED");
        OffsetDateTime publishedAt = now();
        release.setPublishedAt(publishedAt);
        if (release.getEnforcementAt() == null) {
            release.setEnforcementAt(publishedAt.plusDays(
                properties.getAppRelease().getPairedDeviceGraceDays()));
        }
        release.setUpdatedAt(publishedAt);
        releaseMapper.updateById(release);
        return toVo(release);
    }

    @Transactional(rollbackFor = Exception.class)
    public AppReleaseVo update(Long id, AppReleaseUpdateRequest request) {
        PmAppRelease release = releaseMapper.selectById(id);
        if (release == null) {
            throw new ServiceException("App 版本不存在");
        }
        if (request.getMinSupportedVersionCode() > release.getVersionCode()) {
            throw new ServiceException("最低支持 versionCode 不能高于当前版本");
        }
        release.setMinSupportedVersionCode(request.getMinSupportedVersionCode());
        release.setUpdateMode(request.getUpdateMode());
        release.setReleaseNotes(request.getReleaseNotes());
        if ("OPTIONAL".equals(request.getUpdateMode())) {
            release.setEnforcementAt(request.getEnforcementAt());
        } else if (request.getEnforcementAt() != null) {
            release.setEnforcementAt(request.getEnforcementAt());
        } else if (release.getEnforcementAt() == null) {
            release.setEnforcementAt(now().plusDays(
                properties.getAppRelease().getPairedDeviceGraceDays()));
        }
        release.setUpdatedAt(now());
        releaseMapper.updateById(release);
        return toVo(release);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteReleases(List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<PmAppRelease> releases = releaseMapper.selectList(
            new LambdaQueryWrapper<PmAppRelease>()
                .eq(PmAppRelease::getPlatform, "ANDROID")
                .in(PmAppRelease::getId, distinctIds));
        if (releases.size() != distinctIds.size()) {
            throw new ServiceException("部分 App 版本不存在");
        }
        PmAppRelease latestPublished = releaseMapper.selectOne(
            new LambdaQueryWrapper<PmAppRelease>()
                .eq(PmAppRelease::getPlatform, "ANDROID")
                .eq(PmAppRelease::getStatus, "PUBLISHED")
                .orderByDesc(PmAppRelease::getVersionCode)
                .last("limit 1"));
        if (latestPublished != null
            && distinctIds.contains(latestPublished.getId())) {
            throw new ServiceException("当前线上最新版本不能删除，请先发布更高版本");
        }
        for (PmAppRelease release : releases) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getAppRelease().getBucket())
                    .key(release.getObjectKey())
                    .build());
            } catch (S3Exception exception) {
                throw new ServiceException("删除 APK 文件失败：" + exception.awsErrorDetails().errorMessage());
            }
        }
        releaseMapper.deleteBatchIds(distinctIds);
    }

    public AppReleaseVo latest() {
        PmAppRelease release = releaseMapper.selectOne(new LambdaQueryWrapper<PmAppRelease>()
            .eq(PmAppRelease::getPlatform, "ANDROID")
            .eq(PmAppRelease::getStatus, "PUBLISHED")
            .orderByDesc(PmAppRelease::getVersionCode)
            .last("limit 1"));
        if (release == null) {
            throw new ServiceException("暂无可用 App 版本");
        }
        return toVo(release);
    }

    public AppReleaseVo latestOrNull() {
        try {
            return latest();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public void assertPairingAllowed(Integer appVersionCode) {
        AppReleaseVo release = latestOrNull();
        if (appVersionCode == null) {
            throw new org.dromara.payment.api.DeviceApiException(
                400,
                "VALIDATION_FAILED",
                "新设备配对必须提供 appVersionCode",
                false,
                false);
        }
        if (release == null || "OPTIONAL".equals(release.updateMode())) {
            return;
        }
        if (appVersionCode < release.minSupportedVersionCode()
            && !newPairingEnforcementAt(release).isAfter(now())) {
            throw new org.dromara.payment.api.DeviceApiException(
                426,
                "UPDATE_REQUIRED",
                "当前 App 版本低于最低支持版本，请先升级",
                false,
                false);
        }
    }

    public DeviceConfigVo decorate(DeviceConfigVo config) {
        AppReleaseVo release = latestOrNull();
        if (release == null) {
            return config;
        }
        config.setMinSupportedVersionCode(release.minSupportedVersionCode());
        config.setEnforcementAt(release.enforcementAt());
        config.setDownloadUrl(release.downloadUrl());
        config.setUpdateMode(release.updateMode());
        return config;
    }

    public void updateDeviceVersion(PmDevice device, Integer appVersionCode) {
        if (appVersionCode != null) {
            device.setAppVersionCode(appVersionCode);
        }
        AppReleaseVo release = latestOrNull();
        if (release == null || !"SECURITY_BLOCK".equals(release.updateMode())
            || device.getAppVersionCode() == null
            || device.getAppVersionCode() >= release.minSupportedVersionCode()) {
            device.setUpdateRequiredAt(null);
            return;
        }
        if (device.getUpdateRequiredAt() == null) {
            device.setUpdateRequiredAt(newPairingEnforcementAt(release));
        }
    }

    public void assertEventUploadAllowed(PmDevice device) {
        AppReleaseVo release = latestOrNull();
        if (release == null || !"SECURITY_BLOCK".equals(release.updateMode())
            || device.getAppVersionCode() == null
            || device.getAppVersionCode() >= release.minSupportedVersionCode()) {
            return;
        }
        OffsetDateTime requiredAt = device.getUpdateRequiredAt();
        if (requiredAt != null && !requiredAt.isAfter(now())) {
            throw new org.dromara.payment.api.DeviceApiException(
                426,
                "UPDATE_REQUIRED",
                "当前 App 已超过升级宽限期，请升级后继续同步",
                false,
                false);
        }
    }

    public String downloadUrl(Long id) {
        PmAppRelease release = releaseMapper.selectById(id);
        if (release == null || !"PUBLISHED".equals(release.getStatus())) {
            throw new ServiceException("App 版本不存在或未发布");
        }
        long expires = Instant.now().getEpochSecond()
            + properties.getAppRelease().getSignedUrlTtlSeconds();
        String token = signDownload(release, expires);
        return trimTrailingSlash(properties.getPublicBaseUrl())
            + "/api/v1/public/app-releases/" + release.getId()
            + "/download?expires=" + expires + "&token=" + token;
    }

    public AppReleaseDownload openDownload(Long id, long expires, String token) {
        long nowEpoch = Instant.now().getEpochSecond();
        if (expires < nowEpoch
            || expires > nowEpoch + properties.getAppRelease().getSignedUrlTtlSeconds()) {
            throw new ServiceException("APK 下载地址已过期");
        }
        PmAppRelease release = releaseMapper.selectById(id);
        if (release == null || !"PUBLISHED".equals(release.getStatus())) {
            throw new ServiceException("App 版本不存在或未发布");
        }
        if (!PaymentCrypto.constantTimeEquals(signDownload(release, expires), token)) {
            throw new ServiceException("APK 下载签名无效");
        }
        ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(properties.getAppRelease().getBucket())
                .key(release.getObjectKey())
                .build());
        return new AppReleaseDownload(
            stream,
            release.getFileSize(),
            "payment-monitor-" + release.getVersionName() + ".apk",
            release.getSha256());
    }

    private AppReleaseVo toVo(PmAppRelease release) {
        return new AppReleaseVo(
            release.getId(), release.getPlatform(), release.getVersionCode(), release.getVersionName(),
            release.getMinSupportedVersionCode(), release.getEnforcementAt(), downloadUrlSafe(release),
            release.getFileSize(), release.getSha256(), release.getSigningCertificateSha256(),
            release.getVerifiedPackageName(), release.getVerificationStatus(), release.getUpdateMode(),
            release.getReleaseNotes(), release.getStatus(), release.getPublishedAt());
    }

    private String downloadUrlSafe(PmAppRelease release) {
        if (!"PUBLISHED".equals(release.getStatus())) {
            return null;
        }
        try {
            return downloadUrl(release.getId());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void ensureBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                .bucket(properties.getAppRelease().getBucket()).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(properties.getAppRelease().getBucket()).build());
        }
    }

    private void validateInspection(
        AppReleaseSaveRequest request,
        ApkInspectionService.ApkInspection inspection
    ) {
        if (!properties.getAppRelease().getExpectedPackageName().equals(inspection.packageName())) {
            throw new ServiceException(
                "APK 包名必须为 " + properties.getAppRelease().getExpectedPackageName());
        }
        if (!request.getVersionCode().equals(inspection.versionCode())) {
            throw new ServiceException("表单 versionCode 与 APK 不一致");
        }
        if (!request.getVersionName().equals(inspection.versionName())) {
            throw new ServiceException("表单 versionName 与 APK 不一致");
        }
        String expectedSigningCertificate = normalizeDigest(
            properties.getAppRelease().getExpectedSigningCertificateSha256());
        if (!expectedSigningCertificate.isBlank()
            && !PaymentCrypto.constantTimeEquals(
                expectedSigningCertificate,
                normalizeDigest(inspection.signingCertificateSha256()))) {
            throw new ServiceException("APK 签名证书与生产发布证书不一致");
        }
    }

    private String normalizeDigest(String value) {
        return value == null
            ? ""
            : value.replace(":", "").replaceAll("\\s+", "").toLowerCase();
    }

    private void rejectDowngrade(Integer versionCode) {
        PmAppRelease latestPublished = releaseMapper.selectOne(
            new LambdaQueryWrapper<PmAppRelease>()
                .eq(PmAppRelease::getPlatform, "ANDROID")
                .eq(PmAppRelease::getStatus, "PUBLISHED")
                .orderByDesc(PmAppRelease::getVersionCode)
                .last("limit 1"));
        if (latestPublished != null && versionCode <= latestPublished.getVersionCode()) {
            throw new ServiceException("versionCode 必须高于当前已发布版本");
        }
    }

    private void deleteObjectQuietly(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getAppRelease().getBucket())
                .key(objectKey)
                .build());
        } catch (Exception ignored) {
            // Orphan cleanup can be retried by storage maintenance.
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private OffsetDateTime newPairingEnforcementAt(AppReleaseVo release) {
        if (release.enforcementAt() != null) {
            return release.enforcementAt();
        }
        return release.publishedAt() != null ? release.publishedAt() : now();
    }

    private String signDownload(PmAppRelease release, long expires) {
        String secret = properties.getAppRelease().getDownloadSigningSecret();
        if (secret == null || secret.isBlank()) {
            throw new ServiceException("APK_DOWNLOAD_SIGNING_SECRET 未配置");
        }
        String payload = release.getId() + "\n" + expires + "\n" + release.getSha256();
        return PaymentCrypto.hmacSha256Hex(secret, payload);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new ServiceException("payment.public-base-url 未配置");
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public record AppReleaseDownload(
        InputStream inputStream,
        long contentLength,
        String filename,
        String sha256
    ) {
    }
}
