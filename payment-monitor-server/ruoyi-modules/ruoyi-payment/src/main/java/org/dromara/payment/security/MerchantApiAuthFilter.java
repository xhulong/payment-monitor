package org.dromara.payment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.payment.api.MerchantApiError;
import org.dromara.payment.api.MerchantApiException;
import org.dromara.payment.api.MerchantApiResponse;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.service.MerchantApiKeyService;
import org.dromara.payment.service.MerchantApiAuditService;
import org.springframework.http.MediaType;
import org.dromara.payment.service.MerchantLifecycleService;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@RequiredArgsConstructor
public class MerchantApiAuthFilter extends OncePerRequestFilter {
    private final MerchantApiKeyService apiKeyService;
    private final MerchantApiRequestGuard requestGuard;
    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final MerchantApiAuditService auditService;
    private final MerchantLifecycleService lifecycleService;
    private final TrustedClientIpResolver clientIpResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/merchant/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        long started = System.nanoTime();
        Long merchantId = null;
        Long apiKeyDatabaseId = null;
        String keyId = request.getHeader("X-Merchant-Key-Id");
        try {
            CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(
                request,
                properties.getMerchantApi().getMaxRequestBytes());
            String versionHeader = request.getHeader("X-Credential-Version");
            String timestampHeader = request.getHeader("X-Timestamp");
            String nonce = request.getHeader("X-Nonce");
            String signature = request.getHeader("X-Signature");
            if (blank(keyId) || blank(versionHeader) || blank(timestampHeader)
                || blank(nonce) || blank(signature)) {
                throw new MerchantApiException(
                    401, "AUTH_MISSING", "缺少商户 API 鉴权请求头", false);
            }

            int version;
            long timestamp;
            try {
                version = Integer.parseInt(versionHeader);
                timestamp = Long.parseLong(timestampHeader);
            } catch (NumberFormatException exception) {
                throw new MerchantApiException(
                    401, "AUTH_MISSING", "商户 API 鉴权请求头格式无效", false);
            }
            if (Math.abs(Instant.now().getEpochSecond() - timestamp)
                > properties.getMerchantApi().getTimestampSkewSeconds()) {
                throw new MerchantApiException(
                    401, "AUTH_TIMESTAMP_EXPIRED", "请求时间戳已过期", true);
            }

            MerchantApiKeyService.AuthMaterial material =
                apiKeyService.authenticate(keyId, version);
            merchantId = material.merchantId();
            if (!lifecycleService.isActive(merchantId)) {
                throw new MerchantApiException(
                    403,
                    "MERCHANT_NOT_ACTIVE",
                    "商户尚未完成开通",
                    false
                );
            }
            apiKeyDatabaseId = material.keyDatabaseId();
            boolean accepted = requestGuard.claimNonce(
                keyId,
                nonce,
                properties.getMerchantApi().getNonceTtlSeconds());
            if (!accepted) {
                throw new MerchantApiException(
                    401, "AUTH_NONCE_REUSED", "请求 nonce 已使用", false);
            }

            String canonical = request.getMethod().toUpperCase() + "\n"
                + request.getRequestURI() + "\n"
                + timestampHeader + "\n"
                + nonce + "\n"
                + PaymentCrypto.sha256Hex(wrapped.getBody());
            String expected = PaymentCrypto.hmacSha256Hex(material.secret(), canonical);
            if (!PaymentCrypto.constantTimeEquals(expected, signature)) {
                requestGuard.releaseNonce(keyId, nonce);
                throw new MerchantApiException(
                    401, "AUTH_SIGNATURE_INVALID", "商户 API 签名无效", false);
            }

            if (!requestGuard.allowRequest(
                keyId,
                properties.getMerchantApi().getRateLimitPerMinute())) {
                throw new MerchantApiException(
                    429, "RATE_LIMITED", "请求过于频繁", true, 60L);
            }

            wrapped.setAttribute(PaymentConstants.MERCHANT_ID_ATTRIBUTE, material.merchantId());
            wrapped.setAttribute(
                PaymentConstants.MERCHANT_API_KEY_ATTRIBUTE,
                material.keyDatabaseId());
            apiKeyService.markUsed(material.keyDatabaseId());
            chain.doFilter(wrapped, response);
            audit(
                merchantId,
                apiKeyDatabaseId,
                keyId,
                request,
                response.getStatus(),
                response.getStatus() < 400 ? "OK" : "HTTP_" + response.getStatus(),
                started);
        } catch (CachedBodyHttpServletRequest.RequestBodyTooLargeException exception) {
            MerchantApiException apiException = new MerchantApiException(
                413, "REQUEST_TOO_LARGE", "请求体超过服务端限制", false);
            writeError(response, apiException);
            audit(merchantId, apiKeyDatabaseId, keyId, request, 413,
                apiException.getCode(), started);
        } catch (MerchantApiException exception) {
            writeError(response, exception);
            audit(merchantId, apiKeyDatabaseId, keyId, request,
                exception.getHttpStatus(), exception.getCode(), started);
        } catch (Exception exception) {
            response.setStatus(500);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                response.getWriter(),
                MerchantApiResponse.failure(
                    new MerchantApiError(
                        "INTERNAL_ERROR",
                        "商户 API 鉴权处理失败",
                        true,
                        null),
                    properties));
            audit(merchantId, apiKeyDatabaseId, keyId, request, 500,
                "INTERNAL_ERROR", started);
        }
    }

    private void audit(
        Long merchantId,
        Long apiKeyId,
        String keyId,
        HttpServletRequest request,
        int status,
        String resultCode,
        long started
    ) {
        try {
            auditService.record(
                merchantId,
                apiKeyId,
                keyId,
                request.getMethod(),
                request.getRequestURI(),
                clientIpResolver.resolve(request),
                status,
                resultCode,
                (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException ignored) {
            // Audit persistence must not alter the merchant API response.
        }
    }

    private void writeError(
        HttpServletResponse response,
        MerchantApiException exception
    ) throws IOException {
        response.setStatus(exception.getHttpStatus());
        if (exception.getRetryAfterSeconds() != null) {
            response.setHeader("Retry-After", exception.getRetryAfterSeconds().toString());
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getWriter(),
            MerchantApiResponse.failure(exception.toError(), properties));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
