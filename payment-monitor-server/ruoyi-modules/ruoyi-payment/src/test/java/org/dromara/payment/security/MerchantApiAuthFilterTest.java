package org.dromara.payment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import org.dromara.payment.api.MerchantApiResponse;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.service.MerchantApiKeyService;
import org.dromara.payment.service.MerchantApiAuditService;
import org.dromara.payment.service.MerchantLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class MerchantApiAuthFilterTest {

    @Test
    void acceptsSignatureOfExactSerializedBodyAndPassesCachedBytes() throws Exception {
        byte[] body = "{\"merchantOrderNo\":\"ORDER-1\",\"amountMinor\":10001}"
            .getBytes(StandardCharsets.UTF_8);
        Fixture fixture = fixture(body, null);
        AtomicReference<ServletRequest> chainedRequest = new AtomicReference<>();
        FilterChain chain = (request, response) -> chainedRequest.set(request);

        fixture.filter().doFilter(fixture.request(), fixture.response(), chain);

        assertEquals(200, fixture.response().getStatus());
        CachedBodyHttpServletRequest wrapped =
            assertInstanceOf(CachedBodyHttpServletRequest.class, chainedRequest.get());
        assertArrayEquals(body, wrapped.getBody());
        assertEquals(7L, wrapped.getAttribute("paymentMerchantId"));
        verify(fixture.apiKeyService()).markUsed(8L);
    }

    @Test
    void rejectsTamperedBodyAndReturnsStableErrorCode() throws Exception {
        byte[] signedBody = "{\"amountMinor\":10001}".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedBody = "{\"amountMinor\":10002}".getBytes(StandardCharsets.UTF_8);
        Fixture fixture = fixture(tamperedBody, signedBody);

        fixture.filter().doFilter(
            fixture.request(),
            fixture.response(),
            (request, response) -> {
                throw new AssertionError("tampered request must not reach controller");
            });
        verify(fixture.requestGuard()).releaseNonce("mk_test", "nonce-1234567890");

        assertEquals(401, fixture.response().getStatus());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(fixture.objectMapper()).writeValue(any(java.io.Writer.class), payload.capture());
        MerchantApiResponse<?> response = assertInstanceOf(
            MerchantApiResponse.class,
            payload.getValue());
        assertNotNull(response.getError());
        assertEquals("AUTH_SIGNATURE_INVALID", response.getError().code());
    }

    @Test
    void rejectsReplayedNonceBeforeControllerInvocation() throws Exception {
        Fixture fixture = fixture("{}".getBytes(StandardCharsets.UTF_8), null, false);
        fixture.filter().doFilter(
            fixture.request(),
            fixture.response(),
            (request, response) -> {
                throw new AssertionError("replayed request must not reach controller");
            });

        assertEquals(401, fixture.response().getStatus());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(fixture.objectMapper()).writeValue(any(java.io.Writer.class), payload.capture());
        MerchantApiResponse<?> response =
            assertInstanceOf(MerchantApiResponse.class, payload.getValue());
        assertEquals("AUTH_NONCE_REUSED", response.getError().code());
    }

    @Test
    void rejectsInactiveMerchantWithStableErrorCode() throws Exception {
        Fixture fixture = fixture("{}".getBytes(StandardCharsets.UTF_8), null);
        when(fixture.lifecycleService().isActive(7L)).thenReturn(false);

        fixture.filter().doFilter(
            fixture.request(),
            fixture.response(),
            (request, response) -> {
                throw new AssertionError("inactive merchant request must not reach controller");
            });

        assertEquals(403, fixture.response().getStatus());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(fixture.objectMapper()).writeValue(any(java.io.Writer.class), payload.capture());
        MerchantApiResponse<?> response =
            assertInstanceOf(MerchantApiResponse.class, payload.getValue());
        assertEquals("MERCHANT_NOT_ACTIVE", response.getError().code());
    }

    private Fixture fixture(byte[] actualBody, byte[] bodyUsedForSignature) {
        return fixture(actualBody, bodyUsedForSignature, true);
    }

    private Fixture fixture(
        byte[] actualBody,
        byte[] bodyUsedForSignature,
        boolean nonceAccepted
    ) {
        PaymentProperties properties = new PaymentProperties();
        MerchantApiKeyService apiKeyService = mock(MerchantApiKeyService.class);
        when(apiKeyService.authenticate("mk_test", 3)).thenReturn(
            new MerchantApiKeyService.AuthMaterial(7L, 8L, "mk_test", "merchant-secret"));
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        MerchantApiRequestGuard requestGuard = mock(MerchantApiRequestGuard.class);
        when(requestGuard.claimNonce(anyString(), anyString(), anyLong()))
            .thenReturn(nonceAccepted);
        when(requestGuard.allowRequest(anyString(), anyInt())).thenReturn(true);
        MerchantLifecycleService lifecycleService = mock(MerchantLifecycleService.class);
        when(lifecycleService.isActive(7L)).thenReturn(true);
        TrustedClientIpResolver clientIpResolver = mock(TrustedClientIpResolver.class);
        when(clientIpResolver.resolve(any())).thenReturn("127.0.0.1");
        MerchantApiAuthFilter filter =
            new MerchantApiAuthFilter(
                apiKeyService,
                requestGuard,
                properties,
                objectMapper,
                mock(MerchantApiAuditService.class),
                lifecycleService,
                clientIpResolver);

        MockHttpServletRequest request = new MockHttpServletRequest(
            "POST",
            "/api/v1/merchant/orders");
        request.setContent(actualBody);
        request.setContentType("application/json");
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = "nonce-1234567890";
        byte[] signedBody = bodyUsedForSignature == null ? actualBody : bodyUsedForSignature;
        String canonical = "POST\n/api/v1/merchant/orders\n"
            + timestamp + "\n" + nonce + "\n" + PaymentCrypto.sha256Hex(signedBody);
        request.addHeader("X-Merchant-Key-Id", "mk_test");
        request.addHeader("X-Credential-Version", "3");
        request.addHeader("X-Timestamp", timestamp);
        request.addHeader("X-Nonce", nonce);
        request.addHeader(
            "X-Signature",
            PaymentCrypto.hmacSha256Hex("merchant-secret", canonical));
        return new Fixture(
            filter,
            apiKeyService,
            requestGuard,
            objectMapper,
            lifecycleService,
            request,
            new MockHttpServletResponse());
    }

    private record Fixture(
        MerchantApiAuthFilter filter,
        MerchantApiKeyService apiKeyService,
        MerchantApiRequestGuard requestGuard,
        ObjectMapper objectMapper,
        MerchantLifecycleService lifecycleService,
        MockHttpServletRequest request,
        MockHttpServletResponse response
    ) {
    }
}
