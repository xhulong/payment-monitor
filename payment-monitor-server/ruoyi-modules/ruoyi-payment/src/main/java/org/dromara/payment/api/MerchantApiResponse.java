package org.dromara.payment.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.dromara.payment.config.PaymentProperties;

import java.time.Instant;

@Data
@AllArgsConstructor
public class MerchantApiResponse<T> {
    private boolean ok;
    private T data;
    private MerchantApiError error;
    private String serverTime;
    private int apiVersion;

    public static <T> MerchantApiResponse<T> success(T data, PaymentProperties properties) {
        return new MerchantApiResponse<>(
            true,
            data,
            null,
            Instant.now().toString(),
            properties.getMerchantApi().getApiVersion());
    }

    public static <T> MerchantApiResponse<T> failure(
        MerchantApiError error,
        PaymentProperties properties
    ) {
        return new MerchantApiResponse<>(
            false,
            null,
            error,
            Instant.now().toString(),
            properties.getMerchantApi().getApiVersion());
    }
}
