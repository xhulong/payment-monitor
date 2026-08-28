package org.dromara.payment.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class MailTemplateServiceTest {

    @Test
    void codeTemplateUsesLuLuPayBrandAndEscapesUserContent() {
        String html = new MailTemplateService().code(
            "重置密码",
            "<script>alert(1)</script>",
            "123456",
            "5 分钟"
        );

        assertTrue(html.contains("LuLuPay"));
        assertTrue(html.contains("码支付"));
        assertFalse(html.contains("LULU"));
        assertFalse(html.contains("噜噜"));
        assertTrue(html.contains("123456"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertFalse(html.contains("tracking"));
    }
}
