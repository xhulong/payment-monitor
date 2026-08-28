package org.dromara.payment.api;

import lombok.Getter;

/**
 * Exception carrying the stable Android API error contract.
 */
@Getter
public class DeviceApiException extends RuntimeException {
    private final int httpStatus;
    private final String errorCode;
    private final boolean retryable;
    private final boolean rePairRequired;
    private final Long retryAfterSeconds;

    public DeviceApiException(int httpStatus,
                              String errorCode,
                              String message,
                              boolean retryable,
                              boolean rePairRequired) {
        this(httpStatus, errorCode, message, retryable, rePairRequired, null);
    }

    public DeviceApiException(int httpStatus,
                              String errorCode,
                              String message,
                              boolean retryable,
                              boolean rePairRequired,
                              Long retryAfterSeconds) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.rePairRequired = rePairRequired;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public DeviceApiError toError() {
        return new DeviceApiError(
            errorCode, getMessage(), retryable, rePairRequired, retryAfterSeconds);
    }
}
