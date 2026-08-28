package org.dromara.payment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.payment.api.DeviceApiError;
import org.dromara.payment.api.DeviceApiException;
import org.dromara.payment.api.DeviceApiResponse;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.PmDevice;
import org.dromara.payment.service.PaymentDeviceService;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * 设备 HMAC 鉴权过滤器。
 */
@RequiredArgsConstructor
public class DeviceAuthFilter extends OncePerRequestFilter {

    private final PaymentDeviceService deviceService;
    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/api/v1/devices/pair".equals(path)) {
            return true;
        }
        return !(path.startsWith("/api/v1/device/") || path.startsWith("/api/v1/payment-events/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        try {
            CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(
                request, properties.getSecurity().getMaxRequestBytes());
            String deviceIdHeader = request.getHeader("X-Device-Id");
            String credentialVersionHeader = request.getHeader("X-Credential-Version");
            String timestampHeader = request.getHeader("X-Timestamp");
            String nonce = request.getHeader("X-Nonce");
            String signature = request.getHeader("X-Signature");
            if (isBlank(deviceIdHeader) || isBlank(credentialVersionHeader)
                || isBlank(timestampHeader) || isBlank(nonce) || isBlank(signature)) {
                writeError(response, new DeviceApiException(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTH_MISSING", "缺少设备鉴权请求头", false, true));
                return;
            }

            long deviceId;
            int credentialVersion;
            long timestamp;
            try {
                deviceId = Long.parseLong(deviceIdHeader);
                credentialVersion = Integer.parseInt(credentialVersionHeader);
                timestamp = Long.parseLong(timestampHeader);
            } catch (NumberFormatException exception) {
                writeError(response, new DeviceApiException(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTH_MISSING", "设备鉴权请求头格式无效", false, true));
                return;
            }
            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - timestamp) > properties.getSecurity().getTimestampSkewSeconds()) {
                writeError(response, new DeviceApiException(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTH_TIMESTAMP_EXPIRED", "请求时间戳已过期", true, false));
                return;
            }

            PmDevice device = deviceService.requireEnabledDevice(deviceId);
            String nonceKey = "payment:nonce:" + deviceId + ":" + nonce;
            boolean nonceAccepted = RedisUtils.setObjectIfAbsent(
                nonceKey, "1", Duration.ofSeconds(properties.getSecurity().getNonceTtlSeconds()));
            if (!nonceAccepted) {
                writeError(response, new DeviceApiException(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTH_NONCE_REUSED", "请求 nonce 已使用", false, false));
                return;
            }

            String bodyHash = PaymentCrypto.sha256Hex(wrapped.getBody());
            String canonical = request.getMethod() + "\n"
                + request.getRequestURI() + "\n"
                + timestampHeader + "\n"
                + nonce + "\n"
                + bodyHash;
            String expected = PaymentCrypto.hmacSha256Hex(
                deviceService.activeSecret(deviceId, credentialVersion), canonical);
            if (!PaymentCrypto.constantTimeEquals(expected, signature)) {
                RedisUtils.deleteObject(nonceKey);
                writeError(response, new DeviceApiException(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTH_SIGNATURE_INVALID", "设备签名无效", false, true));
                return;
            }

            if (request.getRequestURI().startsWith("/api/v1/payment-events/")) {
                deviceService.assertEventUploadAllowed(device);
            }
            wrapped.setAttribute(PaymentConstants.DEVICE_ID_ATTRIBUTE, deviceId);
            wrapped.setAttribute(PaymentConstants.MERCHANT_ID_ATTRIBUTE, device.getMerchantId());
            chain.doFilter(wrapped, response);
        } catch (CachedBodyHttpServletRequest.RequestBodyTooLargeException e) {
            writeError(response, new DeviceApiException(
                HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                "REQUEST_TOO_LARGE", "请求体超过服务端限制", false, false));
        } catch (DeviceApiException e) {
            writeError(response, e);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), DeviceApiResponse.failure(
                new DeviceApiError(
                    "INTERNAL_ERROR", "设备鉴权处理失败", true, false, null),
                properties));
        }
    }

    private void writeError(HttpServletResponse response, DeviceApiException exception) throws IOException {
        response.setStatus(exception.getHttpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getWriter(), DeviceApiResponse.failure(exception.toError(), properties));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
