package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmWebhookEndpoint;
import org.dromara.payment.domain.PmWebhookOutbox;
import org.dromara.payment.domain.bo.WebhookEndpointQueryBo;
import org.dromara.payment.domain.dto.WebhookEndpointSaveRequest;
import org.dromara.payment.domain.vo.WebhookEndpointSecretVo;
import org.dromara.payment.domain.vo.WebhookEndpointVo;
import org.dromara.payment.mapper.WebhookEndpointMapper;
import org.dromara.payment.mapper.WebhookOutboxMapper;
import org.dromara.payment.security.DeviceSecretCipher;
import org.dromara.payment.security.PaymentCrypto;
import org.dromara.payment.security.WebhookUrlValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WebhookEndpointService {

    private final WebhookEndpointMapper mapper;
    private final WebhookOutboxMapper outboxMapper;
    private final DeviceSecretCipher cipher;
    private final WebhookUrlValidator urlValidator;
    private final MerchantAccessService merchantAccessService;
    private final MerchantDisplayService merchantDisplayService;

    public PageResult<WebhookEndpointVo> queryPage(
        WebhookEndpointQueryBo bo,
        PageQuery pageQuery
    ) {
        Long merchantId = MerchantContext.resolveQueryMerchantId(bo.getMerchantId());
        LambdaQueryWrapper<PmWebhookEndpoint> wrapper = new LambdaQueryWrapper<PmWebhookEndpoint>()
            .eq(merchantId != null, PmWebhookEndpoint::getMerchantId, merchantId)
            .like(StringUtils.isNotBlank(bo.getEndpointName()), PmWebhookEndpoint::getEndpointName, bo.getEndpointName())
            .eq(StringUtils.isNotBlank(bo.getStatus()), PmWebhookEndpoint::getStatus, bo.getStatus())
            .orderByDesc(PmWebhookEndpoint::getCreatedAt);
        Page<PmWebhookEndpoint> page = mapper.selectPage(pageQuery.build(), wrapper);
        List<WebhookEndpointVo> rows = page.getRecords().stream().map(this::toVo).toList();
        merchantDisplayService.enrich(
            rows,
            WebhookEndpointVo::getMerchantId,
            WebhookEndpointVo::setMerchantCode,
            WebhookEndpointVo::setMerchantName);
        return PageResult.build(rows, page.getTotal());
    }

    public WebhookEndpointVo queryById(Long id) {
        WebhookEndpointVo vo = toVo(requireAccessible(id));
        merchantDisplayService.enrich(
            List.of(vo),
            WebhookEndpointVo::getMerchantId,
            WebhookEndpointVo::setMerchantCode,
            WebhookEndpointVo::setMerchantName);
        return vo;
    }

    public WebhookEndpointSecretVo create(WebhookEndpointSaveRequest request) {
        urlValidator.validate(request.getEndpointUrl());
        String secret = PaymentCrypto.randomSecret();
        OffsetDateTime timestamp = now();
        PmWebhookEndpoint endpoint = new PmWebhookEndpoint();
        endpoint.setMerchantId(merchantAccessService.requireTargetMerchant(request.getMerchantId(), true));
        endpoint.setEndpointName(request.getEndpointName().trim());
        endpoint.setEndpointUrl(request.getEndpointUrl().trim());
        endpoint.setSecretCiphertext(cipher.encrypt(secret));
        endpoint.setStatus(request.getStatus());
        endpoint.setEventTypes(normalizeEventTypes(request.getEventTypes()));
        endpoint.setPlatformFilter(request.getPlatformFilter());
        endpoint.setCreatedAt(timestamp);
        endpoint.setUpdatedAt(timestamp);
        mapper.insert(endpoint);
        return new WebhookEndpointSecretVo(toVo(endpoint), secret);
    }

    public WebhookEndpointVo update(Long id, WebhookEndpointSaveRequest request) {
        urlValidator.validate(request.getEndpointUrl());
        PmWebhookEndpoint endpoint = requireAccessible(id);
        endpoint.setEndpointName(request.getEndpointName().trim());
        endpoint.setEndpointUrl(request.getEndpointUrl().trim());
        endpoint.setStatus(request.getStatus());
        endpoint.setEventTypes(normalizeEventTypes(request.getEventTypes()));
        endpoint.setPlatformFilter(request.getPlatformFilter());
        endpoint.setUpdatedAt(now());
        mapper.updateById(endpoint);
        return toVo(endpoint);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(List<Long> ids, String status) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<PmWebhookEndpoint> endpoints = mapper.selectList(
            new LambdaQueryWrapper<PmWebhookEndpoint>()
                .in(PmWebhookEndpoint::getId, distinctIds));
        if (endpoints.size() != distinctIds.size()) {
            throw new ServiceException("部分 Webhook 端点不存在或不属于当前商户");
        }
        MerchantContext.requireSingleAccessibleMerchant(
            endpoints.stream().map(PmWebhookEndpoint::getMerchantId).toList());
        OffsetDateTime timestamp = now();
        endpoints.forEach(endpoint -> {
            endpoint.setStatus(status);
            endpoint.setUpdatedAt(timestamp);
            mapper.updateById(endpoint);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteUnused(List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<PmWebhookEndpoint> endpoints = mapper.selectList(
            new LambdaQueryWrapper<PmWebhookEndpoint>()
                .in(PmWebhookEndpoint::getId, distinctIds));
        if (endpoints.size() != distinctIds.size()) {
            throw new ServiceException("部分 Webhook 端点不存在或不属于当前商户");
        }
        Long merchantId = MerchantContext.requireSingleAccessibleMerchant(
            endpoints.stream().map(PmWebhookEndpoint::getMerchantId).toList());
        long deliveries = outboxMapper.selectCount(new LambdaQueryWrapper<PmWebhookOutbox>()
            .eq(PmWebhookOutbox::getMerchantId, merchantId)
            .in(PmWebhookOutbox::getEndpointId, distinctIds));
        if (deliveries > 0) {
            throw new ServiceException("选中的 Webhook 已产生投递记录，只能停用，不能删除");
        }
        mapper.deleteBatchIds(distinctIds);
    }

    public WebhookEndpointSecretVo rotateSecret(Long id) {
        PmWebhookEndpoint endpoint = requireAccessible(id);
        String secret = PaymentCrypto.randomSecret();
        endpoint.setSecretCiphertext(cipher.encrypt(secret));
        endpoint.setUpdatedAt(now());
        mapper.updateById(endpoint);
        return new WebhookEndpointSecretVo(toVo(endpoint), secret);
    }

    public List<PmWebhookEndpoint> enabledEndpoints(
        Long merchantId,
        String eventType,
        String platform
    ) {
        return mapper.selectList(new LambdaQueryWrapper<PmWebhookEndpoint>()
            .eq(PmWebhookEndpoint::getMerchantId, merchantId)
            .eq(PmWebhookEndpoint::getStatus, PaymentConstants.DEVICE_STATUS_ENABLED))
            .stream()
            .filter(item -> parseEventTypes(item.getEventTypes()).contains(eventType))
            .filter(item -> {
                String filter = StringUtils.blankToDefault(item.getPlatformFilter(), "ALL");
                return "ALL".equals(filter) || filter.equals(platform);
            })
            .toList();
    }

    public PmWebhookEndpoint requireForMerchant(Long id, Long merchantId) {
        PmWebhookEndpoint endpoint = mapper.selectOne(new LambdaQueryWrapper<PmWebhookEndpoint>()
            .eq(PmWebhookEndpoint::getId, id)
            .eq(PmWebhookEndpoint::getMerchantId, merchantId)
            .last("limit 1"));
        if (endpoint == null) {
            throw new ServiceException("Webhook 端点不存在");
        }
        return endpoint;
    }

    public PmWebhookEndpoint requireInternal(Long id) {
        PmWebhookEndpoint endpoint = mapper.selectById(id);
        if (endpoint == null) {
            throw new ServiceException("Webhook 端点不存在");
        }
        return endpoint;
    }

    public PmWebhookEndpoint requireAccessible(Long id) {
        PmWebhookEndpoint endpoint = requireInternal(id);
        MerchantContext.requireAccessibleMerchant(endpoint.getMerchantId());
        return endpoint;
    }

    public PmWebhookEndpoint require(Long id) {
        return requireInternal(id);
    }

    public String decryptSecret(PmWebhookEndpoint endpoint) {
        return cipher.decrypt(endpoint.getSecretCiphertext());
    }

    private WebhookEndpointVo toVo(PmWebhookEndpoint endpoint) {
        WebhookEndpointVo vo = new WebhookEndpointVo();
        vo.setId(endpoint.getId());
        vo.setMerchantId(endpoint.getMerchantId());
        vo.setEndpointName(endpoint.getEndpointName());
        vo.setEndpointUrl(endpoint.getEndpointUrl());
        vo.setStatus(endpoint.getStatus());
        vo.setEventTypes(parseEventTypes(endpoint.getEventTypes()));
        vo.setPlatformFilter(StringUtils.blankToDefault(endpoint.getPlatformFilter(), "ALL"));
        vo.setCreatedAt(endpoint.getCreatedAt());
        vo.setUpdatedAt(endpoint.getUpdatedAt());
        return vo;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String normalizeEventTypes(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return "payment.order.paid";
        }
        return String.join(",", eventTypes.stream().distinct().sorted().toList());
    }

    private List<String> parseEventTypes(String value) {
        if (StringUtils.isBlank(value)) {
            return List.of("payment.order.paid");
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .toList();
    }
}
