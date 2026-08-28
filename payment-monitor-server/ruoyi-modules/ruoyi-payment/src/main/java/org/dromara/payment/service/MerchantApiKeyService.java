package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.domain.PmMerchantApiCredential;
import org.dromara.payment.domain.PmMerchantApiKey;
import org.dromara.payment.domain.dto.MerchantApiKeyCreateRequest;
import org.dromara.payment.domain.vo.MerchantApiKeySecretVo;
import org.dromara.payment.domain.vo.MerchantApiKeyVo;
import org.dromara.payment.mapper.MerchantApiCredentialMapper;
import org.dromara.payment.mapper.MerchantApiKeyMapper;
import org.dromara.payment.security.DeviceSecretCipher;
import org.dromara.payment.security.PaymentCrypto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantApiKeyService {
    private final MerchantApiKeyMapper keyMapper;
    private final MerchantApiCredentialMapper credentialMapper;
    private final MerchantAccessService accessService;
    private final DeviceSecretCipher cipher;

    public List<MerchantApiKeyVo> list(Long merchantId) {
        accessService.requireAccessible(merchantId);
        return keyMapper.selectList(new LambdaQueryWrapper<PmMerchantApiKey>()
                .eq(PmMerchantApiKey::getMerchantId, merchantId)
                .orderByDesc(PmMerchantApiKey::getCreatedAt))
            .stream()
            .map(this::toVo)
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public MerchantApiKeySecretVo create(Long merchantId, MerchantApiKeyCreateRequest request) {
        accessService.requireAccessible(merchantId);
        accessService.requireMerchant(merchantId, true);
        OffsetDateTime now = now();
        String secret = PaymentCrypto.randomSecret();
        PmMerchantApiKey key = new PmMerchantApiKey();
        key.setId(IdWorker.getId());
        key.setMerchantId(merchantId);
        key.setKeyId("mk_" + UUID.randomUUID().toString().replace("-", ""));
        key.setKeyName(request.getKeyName().trim());
        key.setStatus(PaymentConstants.DEVICE_STATUS_ENABLED);
        key.setCurrentVersion(1);
        key.setCreatedBy(LoginHelper.getUserId());
        key.setCreatedAt(now);
        key.setUpdatedAt(now);
        keyMapper.insert(key);
        insertCredential(key.getId(), 1, secret, now);
        return new MerchantApiKeySecretVo(toVo(key), secret);
    }

    @Transactional(rollbackFor = Exception.class)
    public MerchantApiKeySecretVo rotate(Long merchantId, Long keyDatabaseId) {
        PmMerchantApiKey key = requireManagedKey(merchantId, keyDatabaseId);
        PmMerchantApiCredential active = activeCredential(key.getId(), key.getCurrentVersion());
        OffsetDateTime now = now();
        if (active != null && active.getRevokedAt() == null) {
            active.setRevokedAt(now);
            credentialMapper.updateById(active);
        }
        int nextVersion = key.getCurrentVersion() + 1;
        String secret = PaymentCrypto.randomSecret();
        insertCredential(key.getId(), nextVersion, secret, now);
        key.setCurrentVersion(nextVersion);
        key.setStatus(PaymentConstants.DEVICE_STATUS_ENABLED);
        key.setUpdatedAt(now);
        keyMapper.updateById(key);
        return new MerchantApiKeySecretVo(toVo(key), secret);
    }

    @Transactional(rollbackFor = Exception.class)
    public MerchantApiKeyVo revoke(Long merchantId, Long keyDatabaseId) {
        PmMerchantApiKey key = requireManagedKey(merchantId, keyDatabaseId);
        OffsetDateTime now = now();
        key.setStatus(PaymentConstants.DEVICE_STATUS_DISABLED);
        key.setUpdatedAt(now);
        keyMapper.updateById(key);
        credentialMapper.selectList(new LambdaQueryWrapper<PmMerchantApiCredential>()
                .eq(PmMerchantApiCredential::getApiKeyId, key.getId())
                .isNull(PmMerchantApiCredential::getRevokedAt))
            .forEach(credential -> {
                credential.setRevokedAt(now);
                credentialMapper.updateById(credential);
            });
        return toVo(key);
    }

    public AuthMaterial authenticate(String keyId, int credentialVersion) {
        PmMerchantApiKey key = keyMapper.selectOne(new LambdaQueryWrapper<PmMerchantApiKey>()
            .eq(PmMerchantApiKey::getKeyId, keyId)
            .last("limit 1"));
        if (key == null || !PaymentConstants.DEVICE_STATUS_ENABLED.equals(key.getStatus())) {
            throw new org.dromara.payment.api.MerchantApiException(
                401, "MERCHANT_KEY_REVOKED", "商户 API Key 不存在或已撤销", false);
        }
        PmMerchant merchant;
        try {
            merchant = accessService.requireMerchant(key.getMerchantId(), true);
        } catch (ServiceException exception) {
            throw new org.dromara.payment.api.MerchantApiException(
                403, "MERCHANT_DISABLED", "商户已停用", false);
        }
        PmMerchantApiCredential credential = activeCredential(key.getId(), credentialVersion);
        if (credential == null || credential.getRevokedAt() != null) {
            throw new org.dromara.payment.api.MerchantApiException(
                401, "MERCHANT_KEY_REVOKED", "商户 API 凭据版本无效", false);
        }
        return new AuthMaterial(
            merchant.getId(),
            key.getId(),
            key.getKeyId(),
            cipher.decrypt(credential.getSecretCiphertext()));
    }

    public void markUsed(Long keyDatabaseId) {
        PmMerchantApiKey key = keyMapper.selectById(keyDatabaseId);
        if (key != null) {
            key.setLastUsedAt(now());
            key.setUpdatedAt(now());
            keyMapper.updateById(key);
        }
    }

    private PmMerchantApiKey requireManagedKey(Long merchantId, Long keyDatabaseId) {
        accessService.requireAccessible(merchantId);
        PmMerchantApiKey key = keyMapper.selectOne(new LambdaQueryWrapper<PmMerchantApiKey>()
            .eq(PmMerchantApiKey::getId, keyDatabaseId)
            .eq(PmMerchantApiKey::getMerchantId, merchantId)
            .last("limit 1"));
        if (key == null) {
            throw new ServiceException("API Key 不存在");
        }
        return key;
    }

    private PmMerchantApiCredential activeCredential(Long apiKeyId, Integer version) {
        return credentialMapper.selectOne(new LambdaQueryWrapper<PmMerchantApiCredential>()
            .eq(PmMerchantApiCredential::getApiKeyId, apiKeyId)
            .eq(PmMerchantApiCredential::getCredentialVersion, version)
            .last("limit 1"));
    }

    private void insertCredential(Long apiKeyId, int version, String secret, OffsetDateTime now) {
        PmMerchantApiCredential credential = new PmMerchantApiCredential();
        credential.setId(IdWorker.getId());
        credential.setApiKeyId(apiKeyId);
        credential.setCredentialVersion(version);
        credential.setSecretCiphertext(cipher.encrypt(secret));
        credential.setCreatedAt(now);
        credentialMapper.insert(credential);
    }

    private MerchantApiKeyVo toVo(PmMerchantApiKey key) {
        MerchantApiKeyVo vo = new MerchantApiKeyVo();
        vo.setId(key.getId());
        vo.setMerchantId(key.getMerchantId());
        vo.setKeyId(key.getKeyId());
        vo.setKeyName(key.getKeyName());
        vo.setStatus(key.getStatus());
        vo.setCurrentVersion(key.getCurrentVersion());
        vo.setLastUsedAt(key.getLastUsedAt());
        vo.setCreatedAt(key.getCreatedAt());
        vo.setUpdatedAt(key.getUpdatedAt());
        return vo;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    public record AuthMaterial(
        Long merchantId,
        Long keyDatabaseId,
        String keyId,
        String secret
    ) {
    }
}
