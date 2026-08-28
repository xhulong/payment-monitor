package org.dromara.payment.api;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Stable error payload for Android device APIs.
 */
@Data
@AllArgsConstructor
public class DeviceApiError {
    private String code;
    private String message;
    private boolean retryable;
    private boolean rePairRequired;
    private Long retryAfterSeconds;
}
