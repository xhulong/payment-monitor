package org.dromara.payment.integration.epay.callback;

import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.integration.epay.application.PaymentIntegrationService;
import org.dromara.payment.integration.epay.domain.PmExternalOrderBinding;
import org.dromara.payment.integration.epay.domain.PmProtocolCallbackDeliveryLog;
import org.dromara.payment.integration.epay.domain.PmProtocolCallbackOutbox;
import org.dromara.payment.integration.epay.mapper.ExternalOrderBindingMapper;
import org.dromara.payment.integration.epay.mapper.ProtocolCallbackDeliveryLogMapper;
import org.dromara.payment.integration.epay.mapper.ProtocolCallbackOutboxMapper;
import org.dromara.payment.integration.epay.protocol.EpaySigner;
import org.dromara.payment.integration.epay.security.EpayDnsGuard;
import org.dromara.payment.integration.epay.security.EpayUrlValidator;
import org.dromara.payment.security.WebhookLogSanitizer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class ProtocolCallbackDeliveryWorker {
    private final ProtocolCallbackOutboxMapper outboxMapper;
    private final ProtocolCallbackDeliveryLogMapper logMapper;
    private final ExternalOrderBindingMapper bindingMapper;
    private final PaymentIntegrationService integrationService;
    private final EpayUrlValidator urlValidator;
    private final EpaySigner signer;
    private final PaymentProperties properties;
    private final WebhookLogSanitizer sanitizer;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public ProtocolCallbackDeliveryWorker(
        ProtocolCallbackOutboxMapper outboxMapper,
        ProtocolCallbackDeliveryLogMapper logMapper,
        ExternalOrderBindingMapper bindingMapper,
        PaymentIntegrationService integrationService,
        EpayUrlValidator urlValidator,
        EpaySigner signer,
        PaymentProperties properties,
        WebhookLogSanitizer sanitizer,
        ObjectMapper objectMapper,
        EpayDnsGuard dnsGuard
    ) {
        this.outboxMapper = outboxMapper;
        this.logMapper = logMapper;
        this.bindingMapper = bindingMapper;
        this.integrationService = integrationService;
        this.urlValidator = urlValidator;
        this.signer = signer;
        this.properties = properties;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
            .dns(dnsGuard)
            .connectTimeout(Duration.ofSeconds(properties.getEasyPay().getConnectTimeoutSeconds()))
            .readTimeout(Duration.ofSeconds(properties.getEasyPay().getRequestTimeoutSeconds()))
            .writeTimeout(Duration.ofSeconds(properties.getEasyPay().getRequestTimeoutSeconds()))
            .followRedirects(false)
            .followSslRedirects(false)
            .build();
    }

    @Scheduled(fixedDelayString = "${payment.easy-pay.worker-delay-ms:1000}")
    public void processDue() {
        if (!properties.getEasyPay().isEnabled()) {
            return;
        }
        OffsetDateTime timestamp = now();
        List<PmProtocolCallbackOutbox> jobs = outboxMapper.claimDue(
            timestamp,
            timestamp.minusSeconds(properties.getEasyPay().getLockTimeoutSeconds()),
            properties.getEasyPay().getMaxBatchSize());
        for (PmProtocolCallbackOutbox job : jobs) {
            try {
                deliver(job);
            } catch (RuntimeException exception) {
                log.error("易支付回调持久化失败，deliveryId={}", job.getDeliveryId(), exception);
            }
        }
    }

    void deliver(PmProtocolCallbackOutbox outbox) {
        int attempt = outbox.getAttemptCount() + 1;
        OffsetDateTime requestAt = now();
        long started = System.nanoTime();
        Integer httpStatus = null;
        String responseExcerpt = null;
        String error = null;
        boolean acknowledged = false;
        boolean retryable = false;
        try {
            PmExternalOrderBinding binding = bindingMapper.selectById(outbox.getBindingId());
            if (binding == null || !outbox.getMerchantId().equals(binding.getMerchantId())
                || !outbox.getIntegrationId().equals(binding.getIntegrationId())) {
                throw new IllegalStateException("易支付外部订单绑定不存在或归属不一致");
            }
            URI target = urlValidator.validate(
                outbox.getTargetUrl(), binding.getAllowedCallbackHosts());
            Map<String, String> params = readParams(outbox.getUnsignedParams());
            String secret = integrationService.decryptSecret(
                outbox.getIntegrationId(), outbox.getCredentialVersion());
            params.put("sign", signer.sign(params, secret));
            params.put("sign_type", "MD5");
            Request request = buildRequest(outbox, target, params);
            try (Response response = httpClient.newCall(request).execute();
                 InputStream body = response.body() == null
                     ? InputStream.nullInputStream() : response.body().byteStream()) {
                httpStatus = response.code();
                byte[] bytes = body.readNBytes(properties.getEasyPay().getMaxResponseBytes() + 1);
                boolean oversized = bytes.length > properties.getEasyPay().getMaxResponseBytes();
                int contentLength = Math.min(bytes.length, properties.getEasyPay().getMaxResponseBytes());
                String raw = new String(bytes, 0, contentLength, StandardCharsets.UTF_8);
                int displayLength = Math.min(contentLength, 4096);
                responseExcerpt = sanitizer.sanitize(
                    new String(bytes, 0, displayLength, StandardCharsets.UTF_8), 4096);
                if (oversized) {
                    responseExcerpt += "…[truncated]";
                }
                acknowledged = !oversized && httpStatus >= 200 && httpStatus < 300
                    && "success".equals(raw.trim());
                retryable = !acknowledged && (httpStatus == 408 || httpStatus == 429
                    || httpStatus >= 500 || (httpStatus >= 200 && httpStatus < 300));
                if (!acknowledged && responseExcerpt.isBlank()) {
                    error = "接入方未返回严格 success ACK";
                }
            }
        } catch (Exception exception) {
            retryable = !(exception instanceof org.dromara.payment.integration.epay.protocol.EpayException);
            error = safeMessage(exception);
        }
        OffsetDateTime responseAt = now();
        saveLog(outbox, attempt, requestAt, responseAt,
            Math.max(0, (System.nanoTime() - started) / 1_000_000),
            httpStatus, responseExcerpt, error, acknowledged);
        update(outbox, attempt, responseAt, httpStatus,
            error == null ? responseExcerpt : error, acknowledged, retryable);
    }

    private Request buildRequest(
        PmProtocolCallbackOutbox outbox,
        URI target,
        Map<String, String> params
    ) {
        Request.Builder builder = new Request.Builder()
            .header("User-Agent", "PaymentMonitor-EasyPay/1.0")
            .header("X-Delivery-Id", outbox.getDeliveryId())
            .header("X-Protocol-Event-Id", outbox.getEventId());
        if ("POST".equals(outbox.getRequestMethod())) {
            FormBody.Builder form = new FormBody.Builder(StandardCharsets.UTF_8);
            params.forEach(form::add);
            return builder.url(target.toString()).post(form.build()).build();
        }
        HttpUrl url = HttpUrl.get(target);
        HttpUrl.Builder urlBuilder = url.newBuilder();
        params.forEach(urlBuilder::addQueryParameter);
        return builder.url(urlBuilder.build()).get().build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readParams(String value) {
        try {
            Map<String, Object> raw = objectMapper.readValue(value, Map.class);
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((key, item) -> result.put(key, item == null ? "" : item.toString()));
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("解析易支付通知参数失败", exception);
        }
    }

    private void update(
        PmProtocolCallbackOutbox outbox,
        int attempt,
        OffsetDateTime timestamp,
        Integer httpStatus,
        String detail,
        boolean acknowledged,
        boolean retryable
    ) {
        outbox.setAttemptCount(attempt);
        outbox.setLockedAt(null);
        outbox.setLastHttpStatus(httpStatus);
        outbox.setLastResponse(sanitizer.sanitize(detail, 4096));
        outbox.setStrictAcknowledged(acknowledged);
        outbox.setUpdatedAt(timestamp);
        if (acknowledged) {
            outbox.setStatus("DELIVERED");
            outbox.setDeliveredAt(timestamp);
            outbox.setLastError(null);
        } else if (retryable && attempt < properties.getEasyPay().getMaxAttempts()) {
            outbox.setStatus("RETRYING");
            outbox.setLastError(sanitizer.sanitize(detail, 1000));
            outbox.setNextAttemptAt(timestamp.plusSeconds(backoffSeconds(attempt)));
        } else {
            outbox.setStatus("DEAD");
            outbox.setLastError(sanitizer.sanitize(detail, 1000));
        }
        outboxMapper.updateById(outbox);
    }

    private void saveLog(
        PmProtocolCallbackOutbox outbox,
        int attempt,
        OffsetDateTime requestAt,
        OffsetDateTime responseAt,
        long duration,
        Integer httpStatus,
        String response,
        String error,
        boolean acknowledged
    ) {
        PmProtocolCallbackDeliveryLog deliveryLog = new PmProtocolCallbackDeliveryLog();
        deliveryLog.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        deliveryLog.setOutboxId(outbox.getId());
        deliveryLog.setDeliveryId(outbox.getDeliveryId());
        deliveryLog.setAttemptNumber(attempt);
        deliveryLog.setRequestAt(requestAt);
        deliveryLog.setResponseAt(responseAt);
        deliveryLog.setDurationMs(duration);
        deliveryLog.setHttpStatus(httpStatus);
        deliveryLog.setResponseExcerpt(sanitizer.sanitize(response, 4096));
        deliveryLog.setErrorMessage(sanitizer.sanitize(error, 1000));
        deliveryLog.setAcknowledged(acknowledged);
        deliveryLog.setCreatedAt(responseAt);
        logMapper.insert(deliveryLog);
    }

    private long backoffSeconds(int attempt) {
        long base = Math.min(21_600L, 30L * (1L << Math.min(10, Math.max(0, attempt - 1))));
        return Math.min(21_600L,
            base + ThreadLocalRandom.current().nextLong(Math.max(1, base / 5 + 1)));
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}