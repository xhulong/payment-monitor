package org.dromara.payment.integration.epay.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.dromara.payment.integration.epay.application.EpayOrderFacade;
import org.dromara.payment.integration.epay.protocol.EpayRequestParser;
import org.dromara.payment.security.TrustedClientIpResolver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class EpayPublicControllerTest {

    @Test
    void classicEndpointsExposeAllThreePathProfiles() throws Exception {
        Method submitGet = EpayPublicController.class.getMethod(
            "submitGet",
            org.springframework.util.MultiValueMap.class);
        Method mapi = EpayPublicController.class.getMethod(
            "mapi",
            org.springframework.util.MultiValueMap.class);
        Method apiGet = EpayPublicController.class.getMethod(
            "apiGet",
            org.springframework.util.MultiValueMap.class,
            HttpServletRequest.class);

        assertEquals(
            Set.of("/submit.php", "/pay/submit.php", "/epay/submit.php"),
            Set.of(submitGet.getAnnotation(GetMapping.class).value()));
        assertEquals(
            Set.of("/mapi.php", "/pay/mapi.php", "/epay/mapi.php"),
            Set.of(mapi.getAnnotation(PostMapping.class).value()));
        assertEquals(
            Set.of("/api.php", "/pay/api.php", "/epay/api.php"),
            Set.of(apiGet.getAnnotation(GetMapping.class).value()));
    }

    @Test
    void mapiReturnsClassicJsonResponse() {
        EpayOrderFacade facade = mock(EpayOrderFacade.class);
        EpayRequestParser parser = mock(EpayRequestParser.class);
        Map<String, String> parsed = Map.of("pid", "1001");
        when(parser.parse(any())).thenReturn(parsed);
        when(facade.create(parsed)).thenReturn(
            new EpayOrderFacade.EpayCreateResult(
                "trade-1",
                "https://pay.example/epay/pay/token",
                "qr-content",
                "https://pay.example/qr.svg",
                ""));
        EpayPublicController controller = new EpayPublicController(
            facade,
            parser,
            mock(TrustedClientIpResolver.class));

        var response = controller.mapi(new LinkedMultiValueMap<>());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().get("code"));
        assertEquals("trade-1", response.getBody().get("trade_no"));
        assertEquals(
            "https://pay.example/epay/pay/token",
            response.getBody().get("payurl"));
    }

    @Test
    void unsupportedQueryActReturnsProtocolErrorWithoutCallingOrderQuery() {
        EpayOrderFacade facade = mock(EpayOrderFacade.class);
        EpayRequestParser parser = mock(EpayRequestParser.class);
        when(parser.parse(any())).thenReturn(Map.of("act", "refund"));
        EpayPublicController controller = new EpayPublicController(
            facade,
            parser,
            mock(TrustedClientIpResolver.class));

        var response = controller.apiGet(
            new LinkedMultiValueMap<>(),
            mock(HttpServletRequest.class));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(-1, response.getBody().get("code"));
        assertTrue(response.getBody().get("msg").toString().contains("不支持"));
        verify(facade, org.mockito.Mockito.never()).query(any(), any(Boolean.class));
    }

    @Test
    void hostedPaymentPageUsesLuLuPayBrand() {
        EpayOrderFacade facade = mock(EpayOrderFacade.class);
        EpayPublicController controller = new EpayPublicController(
            facade,
            mock(EpayRequestParser.class),
            mock(TrustedClientIpResolver.class));
        String token = "12345678901234567890123456789012";

        var response = controller.paymentPage(token);
        String html = new String(response.getBody(), StandardCharsets.UTF_8);

        verify(facade).publicStatus(token);
        assertTrue(html.contains("<title>LuLuPay - 支付订单</title>"));
        assertTrue(html.contains("<h1>LuLuPay 码支付</h1>"));
        assertTrue(html.contains("LuLuPay · 当前依据到账通知确认支付状态"));
        assertFalse(html.contains("噜噜"));
        assertFalse(html.contains("LULU"));
    }
}
