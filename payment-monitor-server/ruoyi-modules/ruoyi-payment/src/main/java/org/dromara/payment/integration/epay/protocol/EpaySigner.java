package org.dromara.payment.integration.epay.protocol;

import org.dromara.payment.security.PaymentCrypto;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class EpaySigner {
    public String canonical(Map<String, String> parameters) {
        return new TreeMap<>(parameters).entrySet().stream()
            .filter(entry -> !"sign".equals(entry.getKey()) && !"sign_type".equals(entry.getKey()))
            .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("&"));
    }

    public String sign(Map<String, String> parameters, String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                .digest((canonical(parameters) + secret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("易支付 MD5 初始化失败", exception);
        }
    }

    public boolean verify(Map<String, String> parameters, String secret) {
        String signType = parameters.get("sign_type");
        if (signType == null || !"MD5".equalsIgnoreCase(signType.trim())) {
            return false;
        }
        return PaymentCrypto.constantTimeEquals(sign(parameters, secret), parameters.get("sign"));
    }
}
