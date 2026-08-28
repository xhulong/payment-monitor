package org.dromara.payment.integration.epay.protocol;

import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class EpayRequestParser {
    private static final Set<String> ALLOWED = Set.of(
        "pid", "type", "out_trade_no", "notify_url", "return_url", "name",
        "money", "param", "sign", "sign_type", "device", "clientip", "act",
        "trade_no", "key");

    public Map<String, String> parse(MultiValueMap<String, String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((name, items) -> {
            if (!ALLOWED.contains(name)) {
                return;
            }
            if (items == null || items.size() != 1) {
                throw new EpayException("请求参数重复或格式不合法");
            }
            String value = items.getFirst();
            if (value != null && value.length() > maximum(name)) {
                throw new EpayException("请求参数长度超过限制");
            }
            result.put(name, value == null ? "" : value);
        });
        return result;
    }

    private int maximum(String name) {
        return switch (name) {
            case "notify_url", "return_url" -> 1000;
            case "param" -> 500;
            case "name" -> 200;
            case "sign", "key" -> 128;
            default -> 64;
        };
    }
}
