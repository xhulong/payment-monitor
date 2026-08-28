package org.dromara.payment.integration.epay.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.PmQrAsset;
import org.dromara.payment.domain.dto.PaymentIntegrationOrderCreateRequest;
import org.dromara.payment.domain.vo.PaymentIntegrationOrderVo;
import org.dromara.payment.integration.epay.domain.PmExternalOrderBinding;
import org.dromara.payment.integration.epay.domain.PmPaymentIntegration;
import org.dromara.payment.integration.epay.domain.PmPaymentIntegrationRoute;
import org.dromara.payment.integration.epay.domain.PmPaymentIntegrationSecret;
import org.dromara.payment.integration.epay.domain.vo.EpayOrderStatusVo;
import org.dromara.payment.integration.epay.mapper.ExternalOrderBindingMapper;
import org.dromara.payment.integration.epay.mapper.PaymentIntegrationRouteMapper;
import org.dromara.payment.integration.epay.protocol.EpayAmounts;
import org.dromara.payment.integration.epay.protocol.EpayException;
import org.dromara.payment.integration.epay.protocol.EpaySigner;
import org.dromara.payment.integration.epay.security.EpayUrlValidator;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.mapper.QrAssetMapper;
import org.dromara.payment.security.PaymentCrypto;
import org.dromara.payment.service.DeviceAssignmentService;
import org.dromara.payment.service.PaymentOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EpayOrderFacade {
    private static final DateTimeFormatter CLASSIC_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final PaymentIntegrationService integrationService;
    private final PaymentIntegrationRouteMapper routeMapper;
    private final ExternalOrderBindingMapper bindingMapper;
    private final PaymentOrderMapper orderMapper;
    private final QrAssetMapper qrAssetMapper;
    private final PaymentOrderService orderService;
    private final EpayUrlValidator urlValidator;
    private final EpaySigner signer;
    private final EpayPayTypeResolver payTypeResolver;
    private final PaymentProperties properties;
    private final DeviceAssignmentService deviceAssignmentService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public EpayCreateResult create(Map<String, String> parameters) {
        requireEnabled();
        requireCreateFields(parameters);
        PmPaymentIntegration integration = integrationService.requireActiveByPid(parameters.get("pid"));
        PmPaymentIntegrationSecret secretRecord = integrationService.activeSecret(integration.getId());
        String secret = integrationService.decryptSecret(secretRecord);
        if (!signer.verify(parameters, secret)) throw new EpayException("签名校验失败");
        String payType = payTypeResolver.resolve(integration, parameters.get("type"));
        String platform = platform(payType);
        long amountMinor = EpayAmounts.toMinor(parameters.get("money"));
        URI notifyUri = urlValidator.validate(parameters.get("notify_url"), integration.getAllowedCallbackHosts());
        URI returnUri = null;
        if (parameters.get("return_url") != null && !parameters.get("return_url").isBlank()) {
            returnUri = urlValidator.validate(parameters.get("return_url"), integration.getAllowedCallbackHosts());
        }
        PmPaymentIntegrationRoute route = selectRoute(integration, payType, platform);
        PmQrAsset asset = requireRouteAsset(integration, route, platform);
        String fingerprint = fingerprint(parameters, payType, amountMinor, notifyUri, returnUri);
        bindingMapper.lockExternalOrder(integration.getId(), parameters.get("out_trade_no"));
        PmExternalOrderBinding existing = findByExternal(integration.getId(), parameters.get("out_trade_no"));
        if (existing != null) return existingResult(existing, fingerprint);

        String internalOrderNo = "EPAY:" + integration.getId() + ":" + parameters.get("out_trade_no");
        PaymentIntegrationOrderVo order = orderService.createForIntegration(
            new PaymentIntegrationOrderCreateRequest(
                integration.getMerchantId(), internalOrderNo, platform, asset.getId(), amountMinor,
                integration.getDefaultExpireSeconds(), parameters.get("name"),
                "易支付外部订单 " + parameters.get("out_trade_no")));
        OffsetDateTime timestamp = now();
        PmExternalOrderBinding binding = new PmExternalOrderBinding();
        binding.setId(IdWorker.getId());
        binding.setMerchantId(integration.getMerchantId());
        binding.setIntegrationId(integration.getId());
        binding.setOrderId(order.orderId());
        binding.setProtocol("EPAY");
        binding.setProtocolProfile(integration.getProfile());
        binding.setExternalOrderNo(parameters.get("out_trade_no"));
        binding.setGatewayTradeNo(gatewayTradeNo());
        binding.setPayType(payType);
        binding.setRequestAmountMinor(amountMinor);
        binding.setNotifyUrl(notifyUri.toString());
        binding.setReturnUrl(returnUri == null ? null : returnUri.toString());
        binding.setPassthroughParam(blankToNull(parameters.get("param")));
        binding.setCredentialVersion(secretRecord.getSecretVersion());
        binding.setNotifyMethod(integration.getNotifyMethod());
        binding.setCallbackPolicy(integration.getCallbackPolicy());
        binding.setAllowedCallbackHosts(integration.getAllowedCallbackHosts());
        binding.setRequestFingerprint(fingerprint);
        binding.setRequestSnapshot(writeSnapshot(parameters));
        binding.setRiskStatus("NORMAL");
        binding.setCreatedAt(timestamp);
        binding.setUpdatedAt(timestamp);
        if (bindingMapper.insertOnConflict(binding) == 0) {
            PmExternalOrderBinding concurrent = findByExternal(integration.getId(), parameters.get("out_trade_no"));
            if (concurrent == null) throw new EpayException("订单创建冲突，请重试");
            return existingResult(concurrent, fingerprint);
        }
        return toCreateResult(binding, order.publicToken(), order.qrContent());
    }

    public Map<String, Object> query(Map<String, String> parameters, boolean secureRequest) {
        requireEnabled();
        if (properties.getEasyPay().isRequireHttpsQuery() && !secureRequest) {
            throw new EpayException("订单查询必须使用 HTTPS");
        }
        String pid = require(parameters, "pid");
        String key = require(parameters, "key");
        PmPaymentIntegration integration = integrationService.requireActiveByPid(pid);
        String expected = integrationService.decryptSecret(integrationService.activeSecret(integration.getId()));
        if (!PaymentCrypto.constantTimeEqualsExact(expected, key)) throw new EpayException("商户密钥错误");
        PmExternalOrderBinding binding = null;
        if (parameters.get("out_trade_no") != null && !parameters.get("out_trade_no").isBlank()) {
            binding = findByExternal(integration.getId(), parameters.get("out_trade_no"));
        } else if (parameters.get("trade_no") != null && !parameters.get("trade_no").isBlank()) {
            binding = bindingMapper.selectOne(new LambdaQueryWrapper<PmExternalOrderBinding>()
                .eq(PmExternalOrderBinding::getIntegrationId, integration.getId())
                .eq(PmExternalOrderBinding::getGatewayTradeNo, parameters.get("trade_no")).last("limit 1"));
        }
        if (binding == null) throw new EpayException("订单不存在");
        PmPaymentOrder order = requireOrder(binding);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 1); result.put("msg", "查询订单号成功");
        result.put("trade_no", binding.getGatewayTradeNo());
        result.put("out_trade_no", binding.getExternalOrderNo());
        result.put("type", binding.getPayType()); result.put("pid", integration.getPid());
        result.put("addtime", CLASSIC_TIME.format(binding.getCreatedAt().toLocalDateTime()));
        result.put("endtime", order.getPaidAt() == null ? null : CLASSIC_TIME.format(order.getPaidAt().toLocalDateTime()));
        result.put("name", order.getSubject());
        result.put("money", EpayAmounts.toYuan(binding.getRequestAmountMinor()));
        result.put("status", successful(binding, order) ? 1 : 0);
        return result;
    }

    public EpayOrderStatusVo publicStatus(String token) {
        requireEnabled();
        PmExternalOrderBinding binding = requireByToken(token);
        PmPaymentOrder order = requireOrder(binding);
        boolean success = successful(binding, order);
        return new EpayOrderStatusVo(
            binding.getGatewayTradeNo(), binding.getExternalOrderNo(), order.getPlatform(),
            binding.getRequestAmountMinor(), order.getPayableAmountMinor(), order.getStatus(),
            order.getConfirmationStatus(), success, success && binding.getReturnUrl() != null,
            "/api/public/payment-orders/" + token + "/qr.svg", order.getExpiresAt(), order.getPaidAt());
    }

    public URI returnTarget(String token) {
        requireEnabled();
        PmExternalOrderBinding binding = requireByToken(token);
        PmPaymentOrder order = requireOrder(binding);
        if (!successful(binding, order)) throw new EpayException("订单尚未达到回跳确认等级");
        if (binding.getReturnUrl() == null) throw new EpayException("订单未配置同步回跳地址");
        PmPaymentIntegration integration = integrationService.requireInternal(binding.getIntegrationId());
        String secret = integrationService.decryptSecret(binding.getIntegrationId(), binding.getCredentialVersion());
        Map<String, String> params = notificationParams(integration, binding, order);
        params.put("sign", signer.sign(params, secret)); params.put("sign_type", "MD5");
        String separator = binding.getReturnUrl().contains("?") ? "&" : "?";
        String query = params.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(java.util.stream.Collectors.joining("&"));
        return URI.create(binding.getReturnUrl() + separator + query);
    }

    public Map<String, String> notificationParams(PmPaymentIntegration integration,
                                                   PmExternalOrderBinding binding,
                                                   PmPaymentOrder order) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", integration.getPid()); params.put("trade_no", binding.getGatewayTradeNo());
        params.put("out_trade_no", binding.getExternalOrderNo()); params.put("type", binding.getPayType());
        params.put("name", order.getSubject() == null ? "支付订单" : order.getSubject());
        params.put("money", EpayAmounts.toYuan(binding.getRequestAmountMinor()));
        params.put("trade_status", "TRADE_SUCCESS");
        if (binding.getPassthroughParam() != null) params.put("param", binding.getPassthroughParam());
        return params;
    }

    public PmExternalOrderBinding findByOrderId(Long orderId) {
        return bindingMapper.selectOne(new LambdaQueryWrapper<PmExternalOrderBinding>()
            .eq(PmExternalOrderBinding::getOrderId, orderId).last("limit 1"));
    }

    public boolean successful(PmExternalOrderBinding binding, PmPaymentOrder order) {
        if (!PaymentConstants.ORDER_STATUS_PAID.equals(order.getStatus())) return false;
        return switch (binding.getCallbackPolicy()) {
            case "NOTIFICATION_MATCHED" -> java.util.Set.of(
                PaymentConstants.CONFIRMATION_NOTIFICATION,
                PaymentConstants.CONFIRMATION_MANUAL,
                PaymentConstants.CONFIRMATION_RECONCILED).contains(order.getConfirmationStatus());
            case "MANUAL_CONFIRMED" -> java.util.Set.of(
                PaymentConstants.CONFIRMATION_MANUAL,
                PaymentConstants.CONFIRMATION_RECONCILED).contains(order.getConfirmationStatus());
            case "RECONCILED" -> PaymentConstants.CONFIRMATION_RECONCILED.equals(order.getConfirmationStatus());
            default -> false;
        };
    }

    private EpayCreateResult existingResult(PmExternalOrderBinding binding, String fingerprint) {
        if (!PaymentCrypto.constantTimeEquals(binding.getRequestFingerprint(), fingerprint)) {
            throw new EpayException("商户订单号已存在且订单参数不一致");
        }
        PmPaymentOrder order = requireOrder(binding);
        PmQrAsset asset = qrAssetMapper.selectById(order.getQrAssetId());
        String content = asset == null ? "" : asset.getQrContentTemplate()
            .replace("{amountMinor}", order.getPayableAmountMinor().toString())
            .replace("{amount}", EpayAmounts.toYuan(order.getPayableAmountMinor()))
            .replace("{orderNo}", order.getMerchantOrderNo());
        return toCreateResult(binding, order.getPublicToken(), content);
    }

    private EpayCreateResult toCreateResult(PmExternalOrderBinding binding, String token, String qrContent) {
        String base = properties.getPublicBaseUrl().replaceAll("/+$", "");
        return new EpayCreateResult(binding.getGatewayTradeNo(), base + "/epay/pay/" + token,
            qrContent, base + "/api/public/payment-orders/" + token + "/qr.svg", "");
    }

    private PmPaymentIntegrationRoute selectRoute(PmPaymentIntegration integration, String payType, String platform) {
        if (!deviceAssignmentService.hasHealthyObserver(integration.getMerchantId(), platform)) {
            throw new EpayException("当前支付方式没有可用的监控设备");
        }
        PmPaymentIntegrationRoute route = routeMapper.selectOne(
            new LambdaQueryWrapper<PmPaymentIntegrationRoute>()
                .eq(PmPaymentIntegrationRoute::getIntegrationId, integration.getId())
                .eq(PmPaymentIntegrationRoute::getMerchantId, integration.getMerchantId())
                .eq(PmPaymentIntegrationRoute::getPayType, payType)
                .eq(PmPaymentIntegrationRoute::getPlatform, platform)
                .eq(PmPaymentIntegrationRoute::getStatus, "0")
                .orderByAsc(PmPaymentIntegrationRoute::getPriority)
                .orderByAsc(PmPaymentIntegrationRoute::getId).last("limit 1"));
        if (route == null) throw new EpayException("当前支付方式未配置可用二维码路由");
        return route;
    }

    private PmQrAsset requireRouteAsset(PmPaymentIntegration integration,
                                        PmPaymentIntegrationRoute route,
                                        String platform) {
        PmQrAsset asset = qrAssetMapper.selectOne(new LambdaQueryWrapper<PmQrAsset>()
            .eq(PmQrAsset::getId, route.getQrAssetId())
            .eq(PmQrAsset::getMerchantId, integration.getMerchantId())
            .eq(PmQrAsset::getPlatform, platform)
            .eq(PmQrAsset::getStatus, "0").last("limit 1"));
        if (asset == null) throw new EpayException("支付路由对应的二维码不可用");
        return asset;
    }

    private String fingerprint(Map<String, String> parameters, String payType, long amountMinor,
                               URI notifyUri, URI returnUri) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("type", payType); values.put("amount", Long.toString(amountMinor));
        values.put("name", parameters.get("name")); values.put("notify_url", notifyUri.toString());
        values.put("return_url", returnUri == null ? "" : returnUri.toString());
        values.put("param", parameters.getOrDefault("param", ""));
        return PaymentCrypto.sha256Hex(signer.canonical(values));
    }

    private PmExternalOrderBinding findByExternal(Long integrationId, String outTradeNo) {
        return bindingMapper.selectOne(new LambdaQueryWrapper<PmExternalOrderBinding>()
            .eq(PmExternalOrderBinding::getIntegrationId, integrationId)
            .eq(PmExternalOrderBinding::getExternalOrderNo, outTradeNo).last("limit 1"));
    }

    private PmExternalOrderBinding requireByToken(String token) {
        PmPaymentOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PmPaymentOrder>()
            .eq(PmPaymentOrder::getPublicToken, token).last("limit 1"));
        if (order == null) throw new EpayException("支付订单不存在");
        PmExternalOrderBinding binding = findByOrderId(order.getId());
        if (binding == null) throw new EpayException("易支付订单绑定不存在");
        return binding;
    }

    private PmPaymentOrder requireOrder(PmExternalOrderBinding binding) {
        PmPaymentOrder order = orderMapper.selectById(binding.getOrderId());
        if (order == null || !order.getMerchantId().equals(binding.getMerchantId())) {
            throw new EpayException("内部支付订单不存在");
        }
        return order;
    }

    private String writeSnapshot(Map<String, String> parameters) {
        Map<String, String> safe = new LinkedHashMap<>(parameters);
        safe.remove("sign"); safe.remove("key");
        try { return objectMapper.writeValueAsString(safe); }
        catch (Exception exception) { throw new ServiceException("保存易支付订单快照失败"); }
    }

    private void requireCreateFields(Map<String, String> parameters) {
        for (String name : java.util.List.of("pid", "out_trade_no", "notify_url",
            "name", "money", "sign", "sign_type")) require(parameters, name);
        if (!parameters.get("pid").matches("\\d{1,32}")) throw new EpayException("PID 格式不合法");
        if (!parameters.get("out_trade_no").matches("[A-Za-z0-9_-]{1,64}"))
            throw new EpayException("商户订单号格式不合法");
        if (!"MD5".equalsIgnoreCase(parameters.get("sign_type").trim())) {
            throw new EpayException("仅支持 sign_type=MD5");
        }
    }

    private void requireEnabled() {
        if (!properties.getEasyPay().isEnabled()) throw new EpayException("易支付接入未启用");
    }

    private String require(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) throw new EpayException("缺少参数 " + name);
        return value.trim();
    }

    private String platform(String type) { return "alipay".equals(type) ? "ALIPAY" : "WECHAT"; }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private String gatewayTradeNo() { return Long.toString(IdWorker.getId()); }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }

    public record EpayCreateResult(String tradeNo, String payUrl, String qrcode,
                                   String imageUrl, String urlScheme) {
        public Map<String, Object> response() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("code", 1); map.put("msg", "success"); map.put("trade_no", tradeNo);
            map.put("payurl", payUrl); map.put("qrcode", qrcode); map.put("img", imageUrl);
            map.put("urlscheme", urlScheme); return map;
        }
    }
}
