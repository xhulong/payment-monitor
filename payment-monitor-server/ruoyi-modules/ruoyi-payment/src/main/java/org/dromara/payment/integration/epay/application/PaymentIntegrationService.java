package org.dromara.payment.integration.epay.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmQrAsset;
import org.dromara.payment.integration.epay.domain.PmPaymentIntegration;
import org.dromara.payment.integration.epay.domain.PmPaymentIntegrationRoute;
import org.dromara.payment.integration.epay.domain.PmPaymentIntegrationSecret;
import org.dromara.payment.integration.epay.domain.bo.PaymentIntegrationQueryBo;
import org.dromara.payment.integration.epay.domain.dto.PaymentIntegrationRouteSaveRequest;
import org.dromara.payment.integration.epay.domain.dto.PaymentIntegrationSaveRequest;
import org.dromara.payment.integration.epay.domain.vo.PaymentIntegrationRouteVo;
import org.dromara.payment.integration.epay.domain.vo.PaymentIntegrationSecretMetadataVo;
import org.dromara.payment.integration.epay.domain.vo.PaymentIntegrationSecretVo;
import org.dromara.payment.integration.epay.domain.vo.PaymentIntegrationVo;
import org.dromara.payment.integration.epay.mapper.PaymentIntegrationMapper;
import org.dromara.payment.integration.epay.mapper.PaymentIntegrationRouteMapper;
import org.dromara.payment.integration.epay.mapper.PaymentIntegrationSecretMapper;
import org.dromara.payment.integration.epay.protocol.EpayException;
import org.dromara.payment.integration.epay.security.EpaySecretCipher;
import org.dromara.payment.integration.epay.security.EpayUrlValidator;
import org.dromara.payment.mapper.QrAssetMapper;
import org.dromara.payment.security.PaymentCrypto;
import org.dromara.payment.service.MerchantDisplayService;
import org.dromara.payment.service.MerchantLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentIntegrationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PaymentIntegrationMapper mapper;
    private final PaymentIntegrationSecretMapper secretMapper;
    private final PaymentIntegrationRouteMapper routeMapper;
    private final QrAssetMapper qrAssetMapper;
    private final EpaySecretCipher cipher;
    private final EpayUrlValidator urlValidator;
    private final MerchantAccessService merchantAccessService;
    private final MerchantDisplayService merchantDisplayService;
    private final MerchantLifecycleService lifecycleService;

    public PageResult<PaymentIntegrationVo> queryPage(PaymentIntegrationQueryBo bo, PageQuery pageQuery) {
        Long merchantId = MerchantContext.resolveQueryMerchantId(bo.getMerchantId());
        var wrapper = new LambdaQueryWrapper<PmPaymentIntegration>()
            .eq(merchantId != null, PmPaymentIntegration::getMerchantId, merchantId)
            .like(StringUtils.isNotBlank(bo.getIntegrationName()),
                PmPaymentIntegration::getIntegrationName, bo.getIntegrationName())
            .eq(StringUtils.isNotBlank(bo.getPid()), PmPaymentIntegration::getPid, bo.getPid())
            .eq(StringUtils.isNotBlank(bo.getStatus()), PmPaymentIntegration::getStatus, bo.getStatus())
            .orderByDesc(PmPaymentIntegration::getCreatedAt);
        Page<PmPaymentIntegration> page = mapper.selectPage(pageQuery.build(), wrapper);
        List<PaymentIntegrationVo> rows = page.getRecords().stream().map(this::toVo).toList();
        enrichMerchants(rows);
        return PageResult.build(rows, page.getTotal());
    }

    public PaymentIntegrationVo queryById(Long id) {
        PaymentIntegrationVo vo = toVo(requireForMerchant(id));
        enrichMerchants(List.of(vo));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentIntegrationSecretVo create(PaymentIntegrationSaveRequest request) {
        Long merchantId = merchantAccessService.requireTargetMerchant(request.getMerchantId(), true);
        lifecycleService.requireActive(merchantId);
        OffsetDateTime timestamp = now();
        PmPaymentIntegration integration = new PmPaymentIntegration();
        integration.setId(IdWorker.getId());
        integration.setMerchantId(merchantId);
        apply(integration, request);
        integration.setProtocol("EPAY");
        integration.setProfile("EPAY_CLASSIC_V1");
        integration.setPid(generatePid());
        integration.setStatus(PaymentConstants.DEVICE_STATUS_ENABLED);
        integration.setCreatedBy(currentUserId());
        integration.setCreatedAt(timestamp);
        integration.setUpdatedAt(timestamp);
        mapper.insert(integration);
        String secret = PaymentCrypto.randomSecret();
        insertSecret(integration.getId(), 1, secret, timestamp);
        PaymentIntegrationVo vo = toVo(integration);
        enrichMerchants(List.of(vo));
        return new PaymentIntegrationSecretVo(vo, secret);
    }

    public PaymentIntegrationVo update(Long id, PaymentIntegrationSaveRequest request) {
        PmPaymentIntegration integration = requireForMerchant(id);
        validateRequestMerchant(integration, request.getMerchantId());
        apply(integration, request);
        integration.setUpdatedAt(now());
        mapper.updateById(integration);
        return queryById(id);
    }

    public PaymentIntegrationVo updateStatus(Long id, String status) {
        PmPaymentIntegration integration = requireForMerchant(id);
        integration.setStatus(status);
        integration.setUpdatedAt(now());
        mapper.updateById(integration);
        return queryById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentIntegrationSecretVo rotateSecret(Long id) {
        PmPaymentIntegration integration = requireForMerchant(id);
        PmPaymentIntegrationSecret current = activeSecret(id);
        OffsetDateTime timestamp = now();
        current.setStatus("RETIRED");
        current.setRetiredAt(timestamp);
        secretMapper.updateById(current);
        String secret = PaymentCrypto.randomSecret();
        insertSecret(id, current.getSecretVersion() + 1, secret, timestamp);
        integration.setUpdatedAt(timestamp);
        mapper.updateById(integration);
        return new PaymentIntegrationSecretVo(queryById(id), secret);
    }

    public PaymentIntegrationVo revokeSecret(Long id, Long secretId) {
        PmPaymentIntegration integration = requireForMerchant(id);
        PmPaymentIntegrationSecret secret = secretMapper.selectOne(
            new LambdaQueryWrapper<PmPaymentIntegrationSecret>()
                .eq(PmPaymentIntegrationSecret::getId, secretId)
                .eq(PmPaymentIntegrationSecret::getIntegrationId, id)
                .last("limit 1"));
        if (secret == null) throw new ServiceException("易支付接入密钥不存在");
        if ("ACTIVE".equals(secret.getStatus())) {
            throw new ServiceException("当前使用中的密钥需先轮换后才能撤销");
        }
        if (!"REVOKED".equals(secret.getStatus())) {
            secret.setStatus("REVOKED");
            secret.setRevokedAt(now());
            secretMapper.updateById(secret);
        }
        return queryById(id);
    }

    public List<PaymentIntegrationRouteVo> routes(Long id) {
        PmPaymentIntegration integration = requireForMerchant(id);
        return routeMapper.selectList(new LambdaQueryWrapper<PmPaymentIntegrationRoute>()
            .eq(PmPaymentIntegrationRoute::getIntegrationId, integration.getId())
            .eq(PmPaymentIntegrationRoute::getMerchantId, integration.getMerchantId())
            .orderByAsc(PmPaymentIntegrationRoute::getPayType)
            .orderByAsc(PmPaymentIntegrationRoute::getPriority))
            .stream().map(this::toRouteVo).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public List<PaymentIntegrationRouteVo> saveRoutes(
        Long id,
        PaymentIntegrationRouteSaveRequest request
    ) {
        PmPaymentIntegration integration = requireForMerchant(id);
        Long merchantId = integration.getMerchantId();
        for (PaymentIntegrationRouteSaveRequest.Item item : request.getRoutes()) {
            String expected = "alipay".equals(item.getPayType()) ? "ALIPAY" : "WECHAT";
            if (!expected.equals(item.getPlatform())) {
                throw new ServiceException("支付类型与二维码平台不一致");
            }
            PmQrAsset asset = qrAssetMapper.selectOne(new LambdaQueryWrapper<PmQrAsset>()
                .eq(PmQrAsset::getId, item.getQrAssetId())
                .eq(PmQrAsset::getMerchantId, merchantId)
                .last("limit 1"));
            if (asset == null || !expected.equals(asset.getPlatform())) {
                throw new ServiceException("二维码不存在、跨商户或平台不一致");
            }
        }
        routeMapper.delete(new LambdaQueryWrapper<PmPaymentIntegrationRoute>()
            .eq(PmPaymentIntegrationRoute::getIntegrationId, id)
            .eq(PmPaymentIntegrationRoute::getMerchantId, merchantId));
        OffsetDateTime timestamp = now();
        for (PaymentIntegrationRouteSaveRequest.Item item : request.getRoutes()) {
            PmPaymentIntegrationRoute route = new PmPaymentIntegrationRoute();
            route.setId(IdWorker.getId());
            route.setIntegrationId(id);
            route.setMerchantId(merchantId);
            route.setPayType(item.getPayType());
            route.setPlatform(item.getPlatform());
            route.setQrAssetId(item.getQrAssetId());
            route.setPriority(item.getPriority());
            route.setStatus(item.getStatus());
            route.setCreatedAt(timestamp);
            route.setUpdatedAt(timestamp);
            routeMapper.insert(route);
        }
        return routes(id);
    }

    public PmPaymentIntegration requireActiveByPid(String pid) {
        PmPaymentIntegration integration = mapper.selectOne(new LambdaQueryWrapper<PmPaymentIntegration>()
            .eq(PmPaymentIntegration::getPid, pid)
            .eq(PmPaymentIntegration::getStatus, PaymentConstants.DEVICE_STATUS_ENABLED)
            .last("limit 1"));
        if (integration == null) throw new EpayException("PID 不存在或接入应用已停用");
        return integration;
    }

    public PmPaymentIntegration requireInternal(Long id) {
        PmPaymentIntegration integration = mapper.selectById(id);
        if (integration == null) throw new ServiceException("支付接入应用不存在");
        return integration;
    }

    public PmPaymentIntegrationSecret activeSecret(Long integrationId) {
        PmPaymentIntegrationSecret secret = secretMapper.selectOne(
            new LambdaQueryWrapper<PmPaymentIntegrationSecret>()
                .eq(PmPaymentIntegrationSecret::getIntegrationId, integrationId)
                .eq(PmPaymentIntegrationSecret::getStatus, "ACTIVE")
                .last("limit 1"));
        if (secret == null) throw new EpayException("接入应用没有可用密钥");
        return secret;
    }

    public String decryptSecret(PmPaymentIntegrationSecret secret) {
        if ("REVOKED".equals(secret.getStatus())) throw new EpayException("接入密钥已撤销");
        return cipher.decrypt(secret.getSecretCiphertext(), secret.getEncryptionKeyId());
    }

    public String decryptSecret(Long integrationId, Integer version) {
        PmPaymentIntegrationSecret secret = secretMapper.selectOne(
            new LambdaQueryWrapper<PmPaymentIntegrationSecret>()
                .eq(PmPaymentIntegrationSecret::getIntegrationId, integrationId)
                .eq(PmPaymentIntegrationSecret::getSecretVersion, version)
                .last("limit 1"));
        if (secret == null) throw new EpayException("订单签名密钥版本不存在");
        return decryptSecret(secret);
    }

    private PmPaymentIntegration requireForMerchant(Long id) {
        PmPaymentIntegration integration = mapper.selectById(id);
        if (integration == null) throw new ServiceException("支付接入应用不存在");
        MerchantContext.requireAccessibleMerchant(integration.getMerchantId());
        return integration;
    }

    private void validateRequestMerchant(
        PmPaymentIntegration integration,
        Long requestedMerchantId
    ) {
        if (requestedMerchantId != null
            && !requestedMerchantId.equals(integration.getMerchantId())) {
            throw new ServiceException("不能将支付接入修改为其他商户");
        }
    }

    private void enrichMerchants(List<PaymentIntegrationVo> rows) {
        merchantDisplayService.enrich(
            rows,
            PaymentIntegrationVo::getMerchantId,
            PaymentIntegrationVo::setMerchantCode,
            PaymentIntegrationVo::setMerchantName);
    }

    private void apply(PmPaymentIntegration integration, PaymentIntegrationSaveRequest request) {
        integration.setIntegrationCode(request.getIntegrationCode().trim());
        integration.setIntegrationName(request.getIntegrationName().trim());
        integration.setDefaultExpireSeconds(request.getDefaultExpireSeconds());
        integration.setNotifyMethod(request.getNotifyMethod());
        integration.setCallbackPolicy(request.getCallbackPolicy());
        integration.setAllowedCallbackHosts(urlValidator.normalizeHosts(request.getAllowedCallbackHosts()));
        integration.setRemark(StringUtils.isBlank(request.getRemark()) ? null : request.getRemark().trim());
    }

    private void insertSecret(Long integrationId, int version, String plain, OffsetDateTime timestamp) {
        EpaySecretCipher.EncryptedSecret encrypted = cipher.encrypt(plain);
        PmPaymentIntegrationSecret secret = new PmPaymentIntegrationSecret();
        secret.setId(IdWorker.getId());
        secret.setIntegrationId(integrationId);
        secret.setSecretVersion(version);
        secret.setSecretCiphertext(encrypted.cipherText());
        secret.setEncryptionKeyId(encrypted.keyId());
        secret.setStatus("ACTIVE");
        secret.setActivatedAt(timestamp);
        secret.setCreatedAt(timestamp);
        secretMapper.insert(secret);
    }

    private String generatePid() {
        for (int attempt = 0; attempt < 32; attempt++) {
            String pid = Long.toString(RANDOM.nextLong(1_000_000_000L, 10_000_000_000L));
            if (mapper.selectCount(new LambdaQueryWrapper<PmPaymentIntegration>()
                .eq(PmPaymentIntegration::getPid, pid)) == 0) return pid;
        }
        throw new ServiceException("生成易支付 PID 失败，请稍后重试");
    }

    private PaymentIntegrationVo toVo(PmPaymentIntegration integration) {
        PaymentIntegrationVo vo = new PaymentIntegrationVo();
        vo.setId(integration.getId());
        vo.setMerchantId(integration.getMerchantId());
        vo.setIntegrationCode(integration.getIntegrationCode());
        vo.setIntegrationName(integration.getIntegrationName());
        vo.setProtocol(integration.getProtocol());
        vo.setProfile(integration.getProfile());
        vo.setPid(integration.getPid());
        vo.setStatus(integration.getStatus());
        vo.setDefaultExpireSeconds(integration.getDefaultExpireSeconds());
        vo.setNotifyMethod(integration.getNotifyMethod());
        vo.setCallbackPolicy(integration.getCallbackPolicy());
        vo.setAllowedCallbackHosts(Arrays.stream(integration.getAllowedCallbackHosts().split(",")).toList());
        vo.setRemark(integration.getRemark());
        List<PmPaymentIntegrationSecret> secrets = secretMapper.selectList(
            new LambdaQueryWrapper<PmPaymentIntegrationSecret>()
                .eq(PmPaymentIntegrationSecret::getIntegrationId, integration.getId())
                .orderByDesc(PmPaymentIntegrationSecret::getSecretVersion));
        PmPaymentIntegrationSecret active = secrets.stream()
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .findFirst().orElse(null);
        vo.setActiveSecretVersion(active == null ? null : active.getSecretVersion());
        vo.setSecrets(secrets.stream().map(item -> {
            PaymentIntegrationSecretMetadataVo metadata = new PaymentIntegrationSecretMetadataVo();
            metadata.setId(item.getId());
            metadata.setSecretVersion(item.getSecretVersion());
            metadata.setStatus(item.getStatus());
            metadata.setActivatedAt(item.getActivatedAt());
            metadata.setRetiredAt(item.getRetiredAt());
            metadata.setRevokedAt(item.getRevokedAt());
            return metadata;
        }).toList());
        vo.setCreatedAt(integration.getCreatedAt());
        vo.setUpdatedAt(integration.getUpdatedAt());
        return vo;
    }

    private PaymentIntegrationRouteVo toRouteVo(PmPaymentIntegrationRoute route) {
        PaymentIntegrationRouteVo vo = new PaymentIntegrationRouteVo();
        vo.setId(route.getId()); vo.setIntegrationId(route.getIntegrationId());
        vo.setPayType(route.getPayType()); vo.setPlatform(route.getPlatform());
        vo.setQrAssetId(route.getQrAssetId()); vo.setPriority(route.getPriority());
        vo.setStatus(route.getStatus()); vo.setUpdatedAt(route.getUpdatedAt());
        PmQrAsset asset = qrAssetMapper.selectById(route.getQrAssetId());
        if (asset != null && asset.getMerchantId().equals(route.getMerchantId())) {
            vo.setQrAssetName(asset.getAssetName()); vo.setQrAssetCode(asset.getAssetCode());
        }
        return vo;
    }

    private Long currentUserId() {
        try { return LoginHelper.isLogin() ? LoginHelper.getUserId() : null; }
        catch (RuntimeException ignored) { return null; }
    }

    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
}
