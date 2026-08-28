package org.dromara.payment.controller.api;

import org.dromara.payment.service.PaymentOrderService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@Tag("dev")
class PublicPaymentPageControllerTest {

    @Test
    void paymentPageRendersQrImageAndOrderPolling() {
        PublicPaymentPageController controller =
            new PublicPaymentPageController(mock(PaymentOrderService.class));
        String token = "12345678901234567890123456789012";

        var response = controller.page(token);
        String html = new String(response.getBody(), StandardCharsets.UTF_8);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(html.contains("id=\"qr-image\""));
        assertTrue(html.contains("order.qrImageUrl"));
        assertTrue(html.contains("setInterval(load,3000)"));
        assertTrue(html.contains("请核对金额后扫码完成支付"));
        assertTrue(html.contains("<title>LuLuPay - 支付订单</title>"));
        assertTrue(html.contains("<h1>LuLuPay 码支付</h1>"));
        assertTrue(html.contains("LuLuPay · 当前依据到账通知确认支付状态"));
        assertFalse(html.contains("噜噜"));
        assertFalse(html.contains("LULU"));
        assertTrue(html.contains("const token=\"" + token + "\""));
        assertFalse(html.contains("__TOKEN__"));
    }
}
