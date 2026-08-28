package org.dromara.payment.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.dromara.payment.config.PaymentProperties;

import java.time.Instant;

/**
 * Versioned response envelope used by Android device APIs.
 */
@Data
@AllArgsConstructor
public class DeviceApiResponse<T> {
    private boolean ok;
    private T data;
    private DeviceApiError error;
    private String serverTime;
    private int protocolVersion;

    public static <T> DeviceApiResponse<T> success(T data, PaymentProperties properties) {
        return new DeviceApiResponse<>(
            true, data, null, Instant.now().toString(), properties.getProtocolVersion());
    }

    public static <T> DeviceApiResponse<T> failure(DeviceApiError error, PaymentProperties properties) {
        return new DeviceApiResponse<>(
            false, null, error, Instant.now().toString(), properties.getProtocolVersion());
    }
}
