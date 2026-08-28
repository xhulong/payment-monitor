package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.api.DeviceApiException;
import org.dromara.payment.domain.PmDevice;
import org.dromara.payment.domain.PmDeviceCredential;
import org.dromara.payment.domain.PmDeviceHeartbeat;
import org.dromara.payment.domain.PmPairingCode;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.bo.PaymentDeviceQueryBo;
import org.dromara.payment.domain.dto.DeviceStatusRequest;
import org.dromara.payment.domain.dto.HeartbeatRequest;
import org.dromara.payment.domain.dto.PairDeviceRequest;
import org.dromara.payment.domain.vo.PairDeviceVo;
import org.dromara.payment.domain.vo.PairingCodeVo;
import org.dromara.payment.domain.vo.PairingStatusVo;
import org.dromara.payment.domain.vo.PaymentDeviceVo;
import org.dromara.payment.mapper.DeviceCredentialMapper;
import org.dromara.payment.mapper.DeviceHeartbeatMapper;
import org.dromara.payment.mapper.PairingCodeMapper;
import org.dromara.payment.mapper.PaymentDeviceMapper;
import org.dromara.payment.mapper.MerchantMapper;
import org.dromara.payment.security.DeviceSecretCipher;
import org.dromara.payment.security.PaymentCrypto;
import org.redisson.api.RateType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 支付监控设备服务。
 */
@Service
@RequiredArgsConstructor
public class PaymentDeviceService {

    private final PaymentDeviceMapper deviceMapper;
    private final DeviceCredentialMapper credentialMapper;
    private final DeviceHeartbeatMapper heartbeatMapper;
    private final PairingCodeMapper pairingCodeMapper;
    private final MerchantMapper merchantMapper;
    private final DeviceSecretCipher secretCipher;
    private final PaymentProperties properties;
    private final DeviceAssignmentService assignmentService;
    private final AppReleaseService appReleaseService;
    private final MerchantAccessService merchantAccessService;
    private final MerchantDisplayService merchantDisplayService;

    public PairingCodeVo createPairingCode(Long requestedMerchantId) {
        Long merchantId = merchantAccessService.requireTargetMerchant(requestedMerchantId, true);
        for (int attempt = 0; attempt < 5; attempt++) {
            String plainCode = PaymentCrypto.randomPairingCode();
            OffsetDateTime now = now();
            PmPairingCode code = new PmPairingCode();
            code.setId(IdWorker.getId());
            code.setMerchantId(merchantId);
            code.setCodeHash(PaymentCrypto.sha256Hex(plainCode));
            code.setExpiresAt(now.plusSeconds(properties.getPairing().getTtlSeconds()));
            code.setCreatedBy(LoginHelper.getUserId());
            code.setCreatedAt(now);
            try {
                pairingCodeMapper.insert(code);
                return new PairingCodeVo(
                    code.getId(),
                    plainCode,
                    code.getExpiresAt(),
                    normalizedPublicBaseUrl(),
                    properties.getQrSchema(),
                    properties.getProtocolVersion());
            } catch (DuplicateKeyException ignored) {
                // A random 8-digit collision is retried with a fresh value.
            }
        }
        throw new ServiceException("生成配对码失败，请稍后重试");
    }

    public PairingStatusVo queryPairingStatus(Long pairingSessionId) {
        PmPairingCode code = pairingCodeMapper.selectOne(
            new LambdaQueryWrapper<PmPairingCode>()
                .eq(PmPairingCode::getId, pairingSessionId)
                .last("limit 1"));
        if (code == null) {
            throw new ServiceException("配对记录不存在");
        }
        MerchantContext.requireAccessibleMerchant(code.getMerchantId());
        if (code.getUsedAt() != null) {
            PmDevice device = code.getUsedByDeviceId() == null
                ? null
                : deviceMapper.selectById(code.getUsedByDeviceId());
            return new PairingStatusVo(
                code.getId(),
                "PAIRED",
                code.getExpiresAt(),
                code.getUsedByDeviceId(),
                device == null ? null : device.getDeviceName(),
                code.getUsedAt());
        }
        String status = code.getExpiresAt() != null && !code.getExpiresAt().isAfter(now())
            ? "EXPIRED"
            : "PENDING";
        return new PairingStatusVo(
            code.getId(),
            status,
            code.getExpiresAt(),
            null,
            null,
            null);
    }

    @Transactional(rollbackFor = Exception.class)
    public PairDeviceVo pair(PairDeviceRequest request, String clientIp) {
        if (request.getProtocolVersion() != properties.getProtocolVersion()) {
            throw new DeviceApiException(
                400, "PROTOCOL_UNSUPPORTED", "客户端协议版本不受支持", false, false);
        }
        rateLimit("payment:pair:ip:" + clientIp);
        String codeHash = PaymentCrypto.sha256Hex(request.getPairingCode().trim());
        rateLimit("payment:pair:code:" + codeHash);
        appReleaseService.assertPairingAllowed(request.getAppVersionCode());

        PmPairingCode code = pairingCodeMapper.selectForUpdate(codeHash);
        OffsetDateTime now = now();
        if (code == null) {
            throw new DeviceApiException(
                400, "PAIRING_CODE_INVALID", "配对码无效", false, false);
        }
        if (code.getUsedAt() != null) {
            throw new DeviceApiException(
                409, "PAIRING_CODE_USED", "配对码已使用", false, false);
        }
        if (code.getExpiresAt().isBefore(now)) {
            throw new DeviceApiException(
                410, "PAIRING_CODE_EXPIRED", "配对码已过期", false, false);
        }

        PmDevice device;
        int nextKeyVersion;
        if (request.getPreviousDeviceId() == null) {
            device = new PmDevice();
            device.setMerchantId(code.getMerchantId());
            device.setStatus(PaymentConstants.DEVICE_STATUS_ENABLED);
            device.setCreatedAt(now);
            nextKeyVersion = 1;
        } else {
            device = deviceMapper.selectById(request.getPreviousDeviceId());
            if (device == null) {
                throw new DeviceApiException(
                    400, "VALIDATION_FAILED", "重新配对设备不存在", false, false);
            }
            if (!code.getMerchantId().equals(device.getMerchantId())) {
                throw new DeviceApiException(
                    400, "VALIDATION_FAILED", "重新配对设备不属于当前商户", false, false);
            }
            if (!PaymentConstants.DEVICE_STATUS_ENABLED.equals(device.getStatus())) {
                throw new DeviceApiException(
                    403, "DEVICE_DISABLED", "设备已禁用", false, true);
            }
            if (StringUtils.isNotBlank(device.getAndroidIdHash())
                && StringUtils.isNotBlank(request.getAndroidIdHash())
                && !device.getAndroidIdHash().equals(request.getAndroidIdHash())) {
                throw new DeviceApiException(
                    400, "VALIDATION_FAILED", "重新配对设备标识不匹配", false, false);
            }
            PmDeviceCredential previousCredential = latestCredential(device.getId());
            nextKeyVersion = previousCredential == null ? 1 : previousCredential.getKeyVersion() + 1;
            if (previousCredential != null && previousCredential.getRevokedAt() == null) {
                previousCredential.setRevokedAt(now);
                credentialMapper.updateById(previousCredential);
            }
        }

        device.setDeviceName(request.getDeviceName());
        device.setAndroidIdHash(request.getAndroidIdHash());
        device.setAppVersion(request.getAppVersion());
        device.setAppVersionCode(request.getAppVersionCode());
        device.setParserVersion(request.getParserVersion());
        device.setPairedAt(now);
        device.setLastSeenAt(now);
        device.setLastIp(clientIp);
        appReleaseService.updateDeviceVersion(device, request.getAppVersionCode());
        device.setUpdatedAt(now);
        if (device.getId() == null) {
            deviceMapper.insert(device);
        } else {
            deviceMapper.updateById(device);
        }

        String plainSecret = PaymentCrypto.randomSecret();
        PmDeviceCredential credential = new PmDeviceCredential();
        credential.setDeviceId(device.getId());
        credential.setSecretCiphertext(secretCipher.encrypt(plainSecret));
        credential.setKeyVersion(nextKeyVersion);
        credential.setCreatedAt(now);
        credentialMapper.insert(credential);

        code.setUsedAt(now);
        code.setUsedByDeviceId(device.getId());
        pairingCodeMapper.updateById(code);
        PmMerchant merchant = merchantMapper.selectById(device.getMerchantId());
        DeviceAssignmentService.AssignmentInfo assignment =
            assignmentService.infoForDevice(device.getMerchantId(), device.getId());
        var latestRelease = appReleaseService.latestOrNull();
        return new PairDeviceVo(
            device.getId(),
            plainSecret,
            nextKeyVersion,
            properties.getHeartbeat().getIntervalSeconds(),
            properties.getHeartbeat().getOnlineThresholdSeconds(),
            properties.getEvents().getMaxBatchSize(),
            properties.getSecurity().getMaxRequestBytes(),
            properties.getEvents().isRawPayloadUploadEnabled(),
            merchant == null ? null : merchant.getMerchantCode(),
            merchant == null ? null : merchant.getName(),
            assignment.role(),
            assignment.platformScope(),
            latestRelease == null ? null : latestRelease.minSupportedVersionCode(),
            latestRelease == null ? null : latestRelease.enforcementAt(),
            latestRelease == null ? null : latestRelease.downloadUrl(),
            latestRelease == null ? null : latestRelease.updateMode());
    }

    public PageResult<PaymentDeviceVo> queryPage(PaymentDeviceQueryBo bo, PageQuery pageQuery) {
        OffsetDateTime threshold = now()
            .minusSeconds(properties.getHeartbeat().getOnlineThresholdSeconds());
        LambdaQueryWrapper<PmDevice> wrapper = new LambdaQueryWrapper<>();
        Long merchantId = MerchantContext.resolveQueryMerchantId(bo.getMerchantId());
        wrapper.eq(merchantId != null, PmDevice::getMerchantId, merchantId);
        wrapper.like(StringUtils.isNotBlank(bo.getDeviceName()), PmDevice::getDeviceName, bo.getDeviceName());
        wrapper.eq(StringUtils.isNotBlank(bo.getStatus()), PmDevice::getStatus, bo.getStatus());
        if (Boolean.TRUE.equals(bo.getOnline())) {
            wrapper.ge(PmDevice::getLastSeenAt, threshold);
        } else if (Boolean.FALSE.equals(bo.getOnline())) {
            wrapper.and(w -> w.isNull(PmDevice::getLastSeenAt).or().lt(PmDevice::getLastSeenAt, threshold));
        }
        wrapper.orderByDesc(PmDevice::getCreatedAt);
        Page<PmDevice> page = deviceMapper.selectPage(pageQuery.build(), wrapper);
        List<PaymentDeviceVo> rows = page.getRecords().stream()
            .map(item -> toVo(item, threshold))
            .toList();
        merchantDisplayService.enrich(
            rows,
            PaymentDeviceVo::getMerchantId,
            PaymentDeviceVo::setMerchantCode,
            PaymentDeviceVo::setMerchantName);
        return PageResult.build(rows, page.getTotal());
    }

    public PaymentDeviceVo queryById(Long id) {
        PmDevice device = requireAdminDevice(id);
        OffsetDateTime threshold = now()
            .minusSeconds(properties.getHeartbeat().getOnlineThresholdSeconds());
        PaymentDeviceVo vo = toVo(device, threshold);
        vo.setRecentHeartbeats(heartbeatMapper.selectVoList(
            new LambdaQueryWrapper<PmDeviceHeartbeat>()
                .eq(PmDeviceHeartbeat::getDeviceId, id)
                .eq(PmDeviceHeartbeat::getMerchantId, device.getMerchantId())
                .orderByDesc(PmDeviceHeartbeat::getHeartbeatAt)
                .last("limit 100")));
        merchantDisplayService.enrich(
            List.of(vo),
            PaymentDeviceVo::getMerchantId,
            PaymentDeviceVo::setMerchantCode,
            PaymentDeviceVo::setMerchantName);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, DeviceStatusRequest request) {
        PmDevice device = requireAdminDevice(id);
        if (request.getStatus() != null) {
            device.setStatus(request.getStatus());
            device.setUpdatedAt(now());
            deviceMapper.updateById(device);
        }
        if (request.isRevokeCredential()) {
            PmDeviceCredential credential = credentialMapper.selectOne(
                new LambdaQueryWrapper<PmDeviceCredential>()
                    .eq(PmDeviceCredential::getDeviceId, id)
                    .isNull(PmDeviceCredential::getRevokedAt)
                    .orderByDesc(PmDeviceCredential::getKeyVersion)
                    .last("limit 1"));
            if (credential != null) {
                credential.setRevokedAt(now());
                credentialMapper.updateById(credential);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(List<Long> ids, String status) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<PmDevice> devices = deviceMapper.selectList(new LambdaQueryWrapper<PmDevice>()
            .in(PmDevice::getId, distinctIds));
        if (devices.size() != distinctIds.size()) {
            throw new ServiceException("部分设备不存在或不属于当前商户");
        }
        MerchantContext.requireSingleAccessibleMerchant(
            devices.stream().map(PmDevice::getMerchantId).toList());
        OffsetDateTime timestamp = now();
        devices.forEach(device -> {
            device.setStatus(status);
            device.setUpdatedAt(timestamp);
            deviceMapper.updateById(device);
        });
    }

    public PmDevice requireEnabledDevice(Long id) {
        PmDevice device = requireDevice(id);
        if (!PaymentConstants.DEVICE_STATUS_ENABLED.equals(device.getStatus())) {
            throw new DeviceApiException(
                403, "DEVICE_DISABLED", "设备已禁用", false, true);
        }
        return device;
    }

    public String activeSecret(Long deviceId, Integer credentialVersion) {
        PmDeviceCredential credential = credentialMapper.selectOne(
            new LambdaQueryWrapper<PmDeviceCredential>()
                .eq(PmDeviceCredential::getDeviceId, deviceId)
                .eq(PmDeviceCredential::getKeyVersion, credentialVersion)
                .last("limit 1"));
        if (credential == null || credential.getRevokedAt() != null) {
            throw new DeviceApiException(
                403, "CREDENTIAL_REVOKED", "设备密钥不存在或已撤销", false, true);
        }
        return secretCipher.decrypt(credential.getSecretCiphertext());
    }

    public void heartbeat(Long deviceId, HeartbeatRequest request, String clientIp) {
        PmDevice device = requireEnabledDevice(deviceId);
        OffsetDateTime heartbeatAt = now();
        device.setLastSeenAt(heartbeatAt);
        device.setLastIp(clientIp);
        if (StringUtils.isNotBlank(request.getAppVersion())) {
            device.setAppVersion(request.getAppVersion());
        }
        appReleaseService.updateDeviceVersion(device, request.getAppVersionCode());
        if (StringUtils.isNotBlank(request.getParserVersion())) {
            device.setParserVersion(request.getParserVersion());
        }
        device.setPendingCount(nonNegative(request.getPendingCount()));
        device.setRetryingCount(nonNegative(request.getRetryingCount()));
        device.setRejectedCount(nonNegative(request.getRejectedCount()));
        if (request.getLastSyncAt() != null) {
            device.setLastSyncAt(request.getLastSyncAt().withOffsetSameInstant(ZoneOffset.UTC));
        }
        device.setMonitoringEnabled(Boolean.TRUE.equals(request.getMonitoringEnabled()));
        device.setListenerConnected(Boolean.TRUE.equals(request.getListenerConnected()));
        device.setForegroundRunning(Boolean.TRUE.equals(request.getForegroundRunning()));
        device.setNotificationAccessGranted(Boolean.TRUE.equals(request.getNotificationAccessGranted()));
        device.setBatteryOptimizationIgnored(Boolean.TRUE.equals(request.getBatteryOptimizationIgnored()));
        if (request.getLastNotificationAt() != null) {
            device.setLastNotificationAt(
                request.getLastNotificationAt().withOffsetSameInstant(ZoneOffset.UTC));
        }
        device.setLastHealthIssue(StringUtils.trim(request.getHealthIssue()));
        device.setHealthUpdatedAt(heartbeatAt);
        device.setUpdatedAt(heartbeatAt);
        deviceMapper.updateById(device);

        PmDeviceHeartbeat heartbeat = new PmDeviceHeartbeat();
        heartbeat.setId(IdWorker.getId());
        heartbeat.setMerchantId(device.getMerchantId());
        heartbeat.setDeviceId(deviceId);
        heartbeat.setHeartbeatAt(heartbeatAt);
        heartbeat.setAppVersion(device.getAppVersion());
        heartbeat.setParserVersion(device.getParserVersion());
        heartbeat.setPendingCount(device.getPendingCount());
        heartbeat.setRetryingCount(device.getRetryingCount());
        heartbeat.setRejectedCount(device.getRejectedCount());
        heartbeat.setLastSyncAt(device.getLastSyncAt());
        heartbeat.setClientIp(clientIp);
        heartbeat.setMonitoringEnabled(device.getMonitoringEnabled());
        heartbeat.setListenerConnected(device.getListenerConnected());
        heartbeat.setForegroundRunning(device.getForegroundRunning());
        heartbeat.setNotificationAccessGranted(device.getNotificationAccessGranted());
        heartbeat.setBatteryOptimizationIgnored(device.getBatteryOptimizationIgnored());
        heartbeat.setLastNotificationAt(device.getLastNotificationAt());
        heartbeat.setHealthIssue(device.getLastHealthIssue());
        heartbeatMapper.insert(heartbeat);

        heartbeatMapper.delete(new LambdaQueryWrapper<PmDeviceHeartbeat>()
            .eq(PmDeviceHeartbeat::getDeviceId, deviceId)
            .lt(PmDeviceHeartbeat::getHeartbeatAt, heartbeatAt.minusDays(30)));
    }

    public void recordUpload(Long deviceId) {
        PmDevice device = requireEnabledDevice(deviceId);
        OffsetDateTime timestamp = now();
        device.setLastUploadAt(timestamp);
        device.setLastSeenAt(timestamp);
        device.setUpdatedAt(timestamp);
        deviceMapper.updateById(device);
    }

    public long onlineCount(Long merchantId) {
        OffsetDateTime threshold = now()
            .minusSeconds(properties.getHeartbeat().getOnlineThresholdSeconds());
        return deviceMapper.selectCount(new LambdaQueryWrapper<PmDevice>()
            .eq(merchantId != null, PmDevice::getMerchantId, merchantId)
            .eq(PmDevice::getStatus, PaymentConstants.DEVICE_STATUS_ENABLED)
            .ge(PmDevice::getLastSeenAt, threshold));
    }

    public void assertEventUploadAllowed(PmDevice device) {
        appReleaseService.assertEventUploadAllowed(device);
    }

    private void rateLimit(String key) {
        long result = RedisUtils.rateLimiter(
            key, RateType.OVERALL,
            properties.getPairing().getRateLimitPerMinute(), 60);
        if (result < 0) {
            throw new DeviceApiException(
                429, "RATE_LIMITED", "请求过于频繁，请稍后重试", true, false, 60L);
        }
    }

    private PmDevice requireDevice(Long id) {
        PmDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw new ServiceException("设备不存在");
        }
        return device;
    }

    private PmDevice requireAdminDevice(Long id) {
        PmDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw new ServiceException("设备不存在");
        }
        MerchantContext.requireAccessibleMerchant(device.getMerchantId());
        return device;
    }

    private PmDeviceCredential latestCredential(Long deviceId) {
        return credentialMapper.selectOne(
            new LambdaQueryWrapper<PmDeviceCredential>()
                .eq(PmDeviceCredential::getDeviceId, deviceId)
                .orderByDesc(PmDeviceCredential::getKeyVersion)
                .last("limit 1"));
    }

    private PaymentDeviceVo toVo(PmDevice device, OffsetDateTime threshold) {
        PaymentDeviceVo vo = MapstructUtils.convert(device, PaymentDeviceVo.class);
        vo.setOnline(device.getLastSeenAt() != null && !device.getLastSeenAt().isBefore(threshold));
        return vo;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private String normalizedPublicBaseUrl() {
        return properties.getPublicBaseUrl().replaceAll("/+$", "");
    }
}
