package org.dromara.payment.integration.epay.protocol;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class EpayProtocolTest {

    private final EpaySigner signer = new EpaySigner();

    @Test
    void signsClassicUtf8ParametersWithSortedNonEmptyFields() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("pid", "1001");
        parameters.put("type", "alipay");
        parameters.put("out_trade_no", "ORDER_1");
        parameters.put("notify_url", "https://merchant.example/callback");
        parameters.put("name", "中文订单");
        parameters.put("money", "12.34");
        parameters.put("param", "透传");
        parameters.put("return_url", "");
        parameters.put("sign", "ignored");
        parameters.put("sign_type", "MD5");

        assertEquals(
            "money=12.34&name=中文订单&notify_url=https://merchant.example/callback"
                + "&out_trade_no=ORDER_1&param=透传&pid=1001&type=alipay",
            signer.canonical(parameters));
        assertEquals("f8704641052198be27981012bbe001e1",
            signer.sign(parameters, "SecretAbC123"));

        parameters.put("sign", "F8704641052198BE27981012BBE001E1");
        assertTrue(signer.verify(parameters, "SecretAbC123"));
        parameters.put("sign", "f8704641052198be27981012bbe001e2");
        assertFalse(signer.verify(parameters, "SecretAbC123"));
        parameters.put("sign_type", "RSA");
        assertFalse(signer.verify(parameters, "SecretAbC123"));
    }

    @Test
    void verifiesClassicPageClientSignatureWhenTypeIsOmitted() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("money", "23.45");
        parameters.put("name", "V2BOARD_20260721_001");
        parameters.put("notify_url", "http://callback:18080/v2board-notify");
        parameters.put("out_trade_no", "V2BOARD_20260721_001");
        parameters.put("pid", "1000000001");
        parameters.put("return_url", "http://callback:18080/v2board-return");
        parameters.put("sign", "05af4495153b0db4f3e4fb7662fbd17d");
        parameters.put("sign_type", "MD5");

        assertTrue(signer.verify(parameters, "SecretAbC123"));
    }

    @Test
    void convertsMoneyWithoutFloatingPointRounding() {
        assertEquals(1L, EpayAmounts.toMinor("0.01"));
        assertEquals(1234L, EpayAmounts.toMinor("12.34"));
        assertEquals("12.34", EpayAmounts.toYuan(1234));
        assertThrows(EpayException.class, () -> EpayAmounts.toMinor("12.345"));
        assertThrows(EpayException.class, () -> EpayAmounts.toMinor("0"));
        assertThrows(EpayException.class, () -> EpayAmounts.toMinor("NaN"));
    }

    @Test
    void rejectsDuplicateKnownParametersAndOversizedValues() {
        EpayRequestParser parser = new EpayRequestParser();
        LinkedMultiValueMap<String, String> duplicated = new LinkedMultiValueMap<>();
        duplicated.add("pid", "1001");
        duplicated.add("pid", "1002");
        assertThrows(EpayException.class, () -> parser.parse(duplicated));

        LinkedMultiValueMap<String, String> oversized = new LinkedMultiValueMap<>();
        oversized.add("out_trade_no", "x".repeat(65));
        assertThrows(EpayException.class, () -> parser.parse(oversized));
    }

    @Test
    void ignoresUnknownCompatibilityFieldsButKeepsKnownFields() {
        EpayRequestParser parser = new EpayRequestParser();
        LinkedMultiValueMap<String, String> values = new LinkedMultiValueMap<>();
        values.add("pid", "1001");
        values.add("device", "pc");
        values.add("vendor_extension", "ignored");

        Map<String, String> result = parser.parse(values);

        assertEquals(Map.of("pid", "1001", "device", "pc"), result);
    }
}
