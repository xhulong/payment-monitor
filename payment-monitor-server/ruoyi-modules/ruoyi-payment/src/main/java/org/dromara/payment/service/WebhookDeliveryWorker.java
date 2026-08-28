package org.dromara.payment.service;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.PmWebhookDeliveryLog;
import org.dromara.payment.domain.PmWebhookEndpoint;
import org.dromara.payment.domain.PmWebhookOutbox;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.mapper.WebhookDeliveryLogMapper;
import org.dromara.payment.mapper.WebhookOutboxMapper;
import org.dromara.payment.security.PaymentCrypto;
import org.dromara.payment.security.WebhookDnsGuard;
import org.dromara.payment.security.WebhookLogSanitizer;
import org.dromara.payment.security.WebhookUrlValidator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class WebhookDeliveryWorker {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final WebhookOutboxMapper outboxMapper;
    private final WebhookDeliveryLogMapper logMapper;
    private final WebhookEndpointService endpointService;
    private final WebhookUrlValidator urlValidator;
    private final PaymentProperties properties;
    private final WebhookLogSanitizer logSanitizer;
    private final OkHttpClient httpClient;

    public WebhookDeliveryWorker(
        WebhookOutboxMapper outboxMapper,
        WebhookDeliveryLogMapper logMapper,
        WebhookEndpointService endpointService,
        WebhookUrlValidator urlValidator,
        PaymentProperties properties,
        WebhookLogSanitizer logSanitizer,
        WebhookDnsGuard dnsGuard
    ) {
        this.outboxMapper = outboxMapper;
        this.logMapper = logMapper;
        this.endpointService = endpointService;
        this.urlValidator = urlValidator;
        this.properties = properties;
        this.logSanitizer = logSanitizer;
        this.httpClient = new OkHttpClient.Builder()
            .dns(dnsGuard)
            .connectTimeout(Duration.ofSeconds(properties.getWebhook().getConnectTimeoutSeconds()))
            .readTimeout(Duration.ofSeconds(properties.getWebhook().getRequestTimeoutSeconds()))
            .writeTimeout(Duration.ofSeconds(properties.getWebhook().getRequestTimeoutSeconds()))
            .followRedirects(false)
            .followSslRedirects(false)
            .build();
    }

    @Scheduled(fixedDelayString = "${payment.webhook.worker-delay-ms:1000}")
    public void processDue() {
        if (!properties.getWebhook().isEnabled()) {
            return;
        }
        OffsetDateTime now = now();
        List<PmWebhookOutbox> jobs = outboxMapper.claimDue(
            now,
            now.minusSeconds(properties.getWebhook().getLockTimeoutSeconds()),
            properties.getWebhook().getMaxBatchSize()
        );
        for (PmWebhookOutbox outbox : jobs) {
            try {
                deliver(outbox);
            } catch (RuntimeException exception) {
                log.error("Webhook delivery persistence failed, deliveryId={}",
                    outbox.getDeliveryId(), exception);
            }
        }
    }

    private void deliver(PmWebhookOutbox outbox) {
        int attempt = outbox.getAttemptCount() + 1;
        OffsetDateTime requestAt = now();
        long started = System.nanoTime();
        Integer httpStatus = null;
        String responseExcerpt = null;
        String errorMessage = null;
        Long retryAfterSeconds = null;
        boolean success = false;
        boolean retryable = false;
        try {
            PmWebhookEndpoint endpoint = endpointService.requireInternal(outbox.getEndpointId());
            if (!endpoint.getMerchantId().equals(outbox.getMerchantId())) {
                throw new PermanentDeliveryException("Webhook 端点与投递任务商户不一致");
            }
            if (!"0".equals(endpoint.getStatus())) {
                throw new PermanentDeliveryException("Webhook 端点已停用");
            }
            URI uri = urlValidator.validate(endpoint.getEndpointUrl());
            String timestamp = Long.toString(OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond());
            String signature = PaymentCrypto.hmacSha256Hex(
                endpointService.decryptSecret(endpoint),
                timestamp + "." + outbox.getPayload()
            );
            String eventId = StringUtils.blankToDefault(
                outbox.getEventId(),
                outbox.getDeliveryId()
            );
            Request request = new Request.Builder()
                .url(uri.toString())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", "PaymentMonitor-Webhook/1.0")
                .header("X-Delivery-Id", outbox.getDeliveryId())
                .header("X-Webhook-Event-Id", eventId)
                .header("X-Webhook-Schema-Version", Integer.toString(
                    PaymentConstants.WEBHOOK_SCHEMA_VERSION))
                .header("X-Webhook-Timestamp", timestamp)
                .header("X-Webhook-Signature", "v1=" + signature)
                .post(RequestBody.create(outbox.getPayload(), JSON))
                .build();
            try (Response response = httpClient.newCall(request).execute();
                 InputStream body = response.body() == null
                     ? InputStream.nullInputStream()
                     : response.body().byteStream()) {
                httpStatus = response.code();
                byte[] bytes = body.readNBytes(properties.getWebhook().getMaxResponseBytes() + 1);
                int displayLength = Math.min(bytes.length, 4096);
                responseExcerpt = logSanitizer.sanitize(
                    new String(bytes, 0, displayLength, StandardCharsets.UTF_8),
                    4096);
                if (bytes.length > properties.getWebhook().getMaxResponseBytes()) {
                    responseExcerpt += "…[truncated]";
                }
                retryAfterSeconds = retryAfter(response);
            }
            success = httpStatus >= 200 && httpStatus < 300;
            retryable = httpStatus == 408 || httpStatus == 429 || httpStatus >= 500;
            if (!success && StringUtils.isBlank(responseExcerpt)) {
                errorMessage = "Webhook 返回 HTTP " + httpStatus;
            }
        } catch (PermanentDeliveryException | ServiceException exception) {
            errorMessage = exception.getMessage();
        } catch (Exception exception) {
            retryable = true;
            errorMessage = safeMessage(exception);
        }

        OffsetDateTime responseAt = now();
        long durationMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        saveLog(
            outbox,
            attempt,
            requestAt,
            responseAt,
            durationMs,
            httpStatus,
            responseExcerpt,
            errorMessage,
            success
        );
        updateOutbox(
            outbox,
            attempt,
            responseAt,
            httpStatus,
            errorMessage == null ? responseExcerpt : errorMessage,
            success,
            retryable,
            retryAfterSeconds
        );
    }

    private void updateOutbox(
        PmWebhookOutbox outbox,
        int attempt,
        OffsetDateTime now,
        Integer httpStatus,
        String error,
        boolean success,
        boolean retryable,
        Long retryAfterSeconds
    ) {
        outbox.setAttemptCount(attempt);
        outbox.setLockedAt(null);
        outbox.setLastHttpStatus(httpStatus);
        outbox.setUpdatedAt(now);
        if (success) {
            outbox.setStatus("DELIVERED");
            outbox.setDeliveredAt(now);
            outbox.setLastError(null);
            outbox.setResolutionStatus("RESOLVED");
        } else if (retryable && attempt < properties.getWebhook().getMaxAttempts()) {
            outbox.setStatus("RETRYING");
            outbox.setLastError(truncate(error, 1000));
            outbox.setNextAttemptAt(now.plusSeconds(retryDelaySeconds(attempt, retryAfterSeconds)));
        } else {
            outbox.setStatus("DEAD");
            outbox.setLastError(truncate(error, 1000));
            outbox.setResolutionStatus("OPEN");
        }
        outboxMapper.updateById(outbox);
    }

    private void saveLog(
        PmWebhookOutbox outbox,
        int attempt,
        OffsetDateTime requestAt,
        OffsetDateTime responseAt,
        long durationMs,
        Integer httpStatus,
        String responseExcerpt,
        String errorMessage,
        boolean success
    ) {
        PmWebhookDeliveryLog log = new PmWebhookDeliveryLog();
        log.setOutboxId(outbox.getId());
        log.setDeliveryId(outbox.getDeliveryId());
        log.setAttemptNumber(attempt);
        log.setRequestAt(requestAt);
        log.setResponseAt(responseAt);
        log.setDurationMs(durationMs);
        log.setHttpStatus(httpStatus);
        log.setResponseExcerpt(logSanitizer.sanitize(responseExcerpt, 4096));
        log.setErrorMessage(logSanitizer.sanitize(errorMessage, 1000));
        log.setSuccess(success);
        log.setCreatedAt(responseAt);
        logMapper.insert(log);
    }

    private long backoffSeconds(int attempt) {
        long base = Math.min(21_600L, 30L * (1L << Math.min(10, Math.max(0, attempt - 1))));
        return Math.min(21_600L,
            base + ThreadLocalRandom.current().nextLong(Math.max(1, base / 5 + 1)));
    }

    private long retryDelaySeconds(int attempt, Long retryAfterSeconds) {
        if (retryAfterSeconds == null) {
            return backoffSeconds(attempt);
        }
        return Math.clamp(retryAfterSeconds, 1L, 21_600L);
    }

    private Long retryAfter(Response response) {
        String value = response.header("Retry-After");
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Math.max(1, Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            try {
                long seconds = Duration.between(
                    ZonedDateTime.now(ZoneOffset.UTC),
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                ).toSeconds();
                return Math.max(1, seconds);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
            + (StringUtils.isBlank(message) ? "" : ": " + message);
    }

    private String truncate(String value, int maximum) {
        if (value == null || value.length() <= maximum) {
            return value;
        }
        return value.substring(0, maximum);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static class PermanentDeliveryException extends RuntimeException {
        PermanentDeliveryException(String message) {
            super(message);
        }
    }
}
