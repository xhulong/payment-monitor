package org.dromara.payment.integration.epay.callback;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class ProtocolCallbackDeliveryWorkerTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void deliversGetAndRequiresStrictSuccessAcknowledgement() throws Exception {
        CapturedRequest captured = new CapturedRequest();
        WorkerFixture fixture = fixture(startServer(200, " success ", captured), "GET", 1024);

        fixture.worker().deliver(fixture.outbox());

        assertEquals("DELIVERED", fixture.outbox().getStatus());
        assertTrue(Boolean.TRUE.equals(fixture.outbox().getStrictAcknowledged()));
        assertEquals("GET", captured.method);
        Map<String, String> query = parseForm(captured.query);
        assertEquals("1001", query.get("pid"));
        assertEquals("MD5", query.get("sign_type"));
        assertEquals(32, query.get("sign").length());
        verifyLog(fixture.logMapper(), true, 200);
    }

    @Test
    void deliversPostFormWithoutPuttingParametersInQuery() throws Exception {
        CapturedRequest captured = new CapturedRequest();
        WorkerFixture fixture = fixture(startServer(200, "success", captured), "POST", 1024);

        fixture.worker().deliver(fixture.outbox());

        assertEquals("DELIVERED", fixture.outbox().getStatus());
        assertEquals("POST", captured.method);
        assertTrue(captured.query == null || captured.query.isBlank());
        Map<String, String> form = parseForm(captured.body);
        assertEquals("ORDER-1", form.get("out_trade_no"));
        assertEquals("MD5", form.get("sign_type"));
        verifyLog(fixture.logMapper(), true, 200);
    }

    @Test
    void retriesTwoHundredWithoutStrictSuccessAndRateLimitResponses() throws Exception {
        CapturedRequest captured = new CapturedRequest();
        WorkerFixture nonAck = fixture(startServer(200, "ok", captured), "GET", 1024);

        nonAck.worker().deliver(nonAck.outbox());

        assertEquals("RETRYING", nonAck.outbox().getStatus());
        assertEquals("ok", nonAck.outbox().getLastResponse());
        stopServer();
        server = null;

        WorkerFixture rateLimited = fixture(startServer(429, "slow down", new CapturedRequest()), "GET", 1024);
        rateLimited.worker().deliver(rateLimited.outbox());

        assertEquals("RETRYING", rateLimited.outbox().getStatus());
        assertEquals(429, rateLimited.outbox().getLastHttpStatus());
    }

    @Test
    void oversizedResponseCannotBeAcceptedEvenWhenItStartsWithSuccess() throws Exception {
        String response = "success" + " ".repeat(128);
        WorkerFixture fixture = fixture(startServer(200, response, new CapturedRequest()), "GET", 16);

        fixture.worker().deliver(fixture.outbox());

        assertEquals("RETRYING", fixture.outbox().getStatus());
        assertTrue(fixture.outbox().getLastResponse().contains("truncated"));
    }

    private WorkerFixture fixture(String targetUrl, String method, int maxResponseBytes) {
        ProtocolCallbackOutboxMapper outboxMapper = mock(ProtocolCallbackOutboxMapper.class);
        ProtocolCallbackDeliveryLogMapper logMapper = mock(ProtocolCallbackDeliveryLogMapper.class);
        ExternalOrderBindingMapper bindingMapper = mock(ExternalOrderBindingMapper.class);
        PaymentIntegrationService integrationService = mock(PaymentIntegrationService.class);
        PaymentProperties properties = new PaymentProperties();
        properties.getEasyPay().setAllowHttp(true);
        properties.getEasyPay().setAllowPrivateNetwork(true);
        properties.getEasyPay().setMaxResponseBytes(maxResponseBytes);
        properties.getEasyPay().setMaxAttempts(3);
        properties.getEasyPay().setConnectTimeoutSeconds(1);
        properties.getEasyPay().setRequestTimeoutSeconds(2);

        PmExternalOrderBinding binding = new PmExternalOrderBinding();
        binding.setId(10L);
        binding.setMerchantId(1L);
        binding.setIntegrationId(2L);
        binding.setAllowedCallbackHosts("127.0.0.1");
        when(bindingMapper.selectById(10L)).thenReturn(binding);
        when(integrationService.decryptSecret(2L, 1)).thenReturn("BusinessKeyAbC");

        PmProtocolCallbackOutbox outbox = new PmProtocolCallbackOutbox();
        outbox.setId(20L);
        outbox.setDeliveryId("delivery-1");
        outbox.setEventId("event-1");
        outbox.setMerchantId(1L);
        outbox.setIntegrationId(2L);
        outbox.setBindingId(10L);
        outbox.setTargetUrl(targetUrl);
        outbox.setRequestMethod(method);
        outbox.setCredentialVersion(1);
        outbox.setUnsignedParams("{\"pid\":\"1001\",\"out_trade_no\":\"ORDER-1\",\"money\":\"12.34\"}");
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC));
        outbox.setStrictAcknowledged(false);

        EpayUrlValidator validator = new EpayUrlValidator(properties);
        ProtocolCallbackDeliveryWorker worker = new ProtocolCallbackDeliveryWorker(
            outboxMapper,
            logMapper,
            bindingMapper,
            integrationService,
            validator,
            new EpaySigner(),
            properties,
            new WebhookLogSanitizer(),
            JsonMapper.builder().build(),
            new EpayDnsGuard(validator));
        return new WorkerFixture(worker, outbox, logMapper);
    }

    private String startServer(int status, String response, CapturedRequest captured) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/callback", exchange -> handle(exchange, status, response, captured));
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/callback";
    }

    private void handle(HttpExchange exchange, int status, String response, CapturedRequest captured)
        throws IOException {
        captured.method = exchange.getRequestMethod();
        captured.query = exchange.getRequestURI().getRawQuery();
        captured.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private Map<String, String> parseForm(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value == null || value.isBlank()) return result;
        for (String item : value.split("&")) {
            String[] pair = item.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8));
        }
        return result;
    }

    private void verifyLog(ProtocolCallbackDeliveryLogMapper mapper, boolean acknowledged, int status) {
        ArgumentCaptor<PmProtocolCallbackDeliveryLog> captor =
            ArgumentCaptor.forClass(PmProtocolCallbackDeliveryLog.class);
        verify(mapper).insert(captor.capture());
        assertEquals(acknowledged, captor.getValue().getAcknowledged());
        assertEquals(status, captor.getValue().getHttpStatus());
    }

    private record WorkerFixture(
        ProtocolCallbackDeliveryWorker worker,
        PmProtocolCallbackOutbox outbox,
        ProtocolCallbackDeliveryLogMapper logMapper
    ) {
    }

    private static final class CapturedRequest {
        private String method;
        private String query;
        private String body;
    }
}
