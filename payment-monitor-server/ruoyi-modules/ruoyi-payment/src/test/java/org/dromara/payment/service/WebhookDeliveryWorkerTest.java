package org.dromara.payment.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.PmWebhookDeliveryLog;
import org.dromara.payment.domain.PmWebhookEndpoint;
import org.dromara.payment.domain.PmWebhookOutbox;
import org.dromara.payment.mapper.WebhookDeliveryLogMapper;
import org.dromara.payment.mapper.WebhookOutboxMapper;
import org.dromara.payment.security.PaymentCrypto;
import org.dromara.payment.security.WebhookUrlValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class WebhookDeliveryWorkerTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void signsAndDeliversTheExactUtf8RequestBody() throws Exception {
        AtomicInteger responseStatus = new AtomicInteger(204);
        List<CapturedRequest> requests = new ArrayList<>();
        String endpointUrl = startServer(responseStatus, null, requests);
        WorkerFixture fixture = fixture(endpointUrl, "{\"message\":\"支付成功\"}");

        fixture.worker().processDue();

        assertEquals("DELIVERED", fixture.outbox().getStatus());
        assertEquals(1, fixture.outbox().getAttemptCount());
        assertEquals(1, requests.size());
        CapturedRequest request = requests.getFirst();
        assertEquals(fixture.outbox().getDeliveryId(), request.deliveryId());
        assertEquals(fixture.outbox().getPayload(), request.body());
        String expected = "v1=" + PaymentCrypto.hmacSha256Hex(
            "webhook-secret",
            request.timestamp() + "." + fixture.outbox().getPayload()
        );
        assertEquals(expected, request.signature());

        ArgumentCaptor<PmWebhookDeliveryLog> logCaptor =
            ArgumentCaptor.forClass(PmWebhookDeliveryLog.class);
        verify(fixture.logMapper()).insert(logCaptor.capture());
        assertTrue(logCaptor.getValue().getSuccess());
        assertEquals(204, logCaptor.getValue().getHttpStatus());
    }

    @Test
    void retryAfterIsHonoredAndDeliveryIdStaysStableAcrossRetries() throws Exception {
        AtomicInteger responseStatus = new AtomicInteger(429);
        List<CapturedRequest> requests = new ArrayList<>();
        String endpointUrl = startServer(responseStatus, "120", requests);
        WorkerFixture fixture = fixture(endpointUrl, "{\"type\":\"payment.order.paid\"}");
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        fixture.worker().processDue();

        assertEquals("RETRYING", fixture.outbox().getStatus());
        assertEquals(429, fixture.outbox().getLastHttpStatus());
        assertTrue(fixture.outbox().getNextAttemptAt().isAfter(before.plusSeconds(118)));

        responseStatus.set(200);
        fixture.worker().processDue();

        assertEquals("DELIVERED", fixture.outbox().getStatus());
        assertEquals(2, fixture.outbox().getAttemptCount());
        assertEquals(2, requests.size());
        assertEquals(requests.get(0).deliveryId(), requests.get(1).deliveryId());
        assertEquals(requests.get(0).body(), requests.get(1).body());
    }

    @Test
    void networkFailureSchedulesRetryInsteadOfLosingTheOutbox() {
        WorkerFixture fixture = fixture(
            "http://127.0.0.1:1/webhook",
            "{\"type\":\"payment.order.paid\"}"
        );

        fixture.worker().processDue();

        assertEquals("RETRYING", fixture.outbox().getStatus());
        assertEquals(1, fixture.outbox().getAttemptCount());
        assertTrue(fixture.outbox().getLastError().contains("ConnectException"));
    }

    private WorkerFixture fixture(String endpointUrl, String payload) {
        WebhookOutboxMapper outboxMapper = mock(WebhookOutboxMapper.class);
        WebhookDeliveryLogMapper logMapper = mock(WebhookDeliveryLogMapper.class);
        WebhookEndpointService endpointService = mock(WebhookEndpointService.class);
        PaymentProperties properties = new PaymentProperties();
        properties.getWebhook().setAllowHttp(true);
        properties.getWebhook().setAllowPrivateNetwork(true);
        properties.getWebhook().setConnectTimeoutSeconds(1);
        properties.getWebhook().setRequestTimeoutSeconds(2);
        properties.getWebhook().setMaxAttempts(3);

        PmWebhookOutbox outbox = new PmWebhookOutbox();
        outbox.setId(100L);
        outbox.setDeliveryId("delivery-fixed-id");
        outbox.setMerchantId(1L);
        outbox.setEndpointId(10L);
        outbox.setPayload(payload);
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(outboxMapper.claimDue(any(), any(), anyInt())).thenReturn(List.of(outbox));

        PmWebhookEndpoint endpoint = new PmWebhookEndpoint();
        endpoint.setId(10L);
        endpoint.setMerchantId(1L);
        endpoint.setEndpointUrl(endpointUrl);
        endpoint.setStatus("0");
        when(endpointService.requireInternal(10L)).thenReturn(endpoint);
        when(endpointService.decryptSecret(endpoint)).thenReturn("webhook-secret");

        WebhookDeliveryWorker worker = new WebhookDeliveryWorker(
            outboxMapper,
            logMapper,
            endpointService,
            new WebhookUrlValidator(properties),
            properties,
            new org.dromara.payment.security.WebhookLogSanitizer(),
            new org.dromara.payment.security.WebhookDnsGuard(new WebhookUrlValidator(properties))
        );
        return new WorkerFixture(worker, outbox, outboxMapper, logMapper);
    }

    private String startServer(
        AtomicInteger responseStatus,
        String retryAfter,
        List<CapturedRequest> requests
    ) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> handle(
            exchange,
            responseStatus.get(),
            retryAfter,
            requests
        ));
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook";
    }

    private void handle(
        HttpExchange exchange,
        int responseStatus,
        String retryAfter,
        List<CapturedRequest> requests
    ) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new CapturedRequest(
            exchange.getRequestHeaders().getFirst("X-Delivery-Id"),
            exchange.getRequestHeaders().getFirst("X-Webhook-Timestamp"),
            exchange.getRequestHeaders().getFirst("X-Webhook-Signature"),
            body
        ));
        if (retryAfter != null && responseStatus == 429) {
            exchange.getResponseHeaders().add("Retry-After", retryAfter);
        }
        byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatus, responseStatus == 204 ? -1 : response.length);
        if (responseStatus != 204) {
            exchange.getResponseBody().write(response);
        }
        exchange.close();
    }

    private record CapturedRequest(
        String deliveryId,
        String timestamp,
        String signature,
        String body
    ) {
    }

    private record WorkerFixture(
        WebhookDeliveryWorker worker,
        PmWebhookOutbox outbox,
        WebhookOutboxMapper outboxMapper,
        WebhookDeliveryLogMapper logMapper
    ) {
    }
}
