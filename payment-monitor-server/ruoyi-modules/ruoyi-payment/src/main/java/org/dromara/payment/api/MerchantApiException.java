package org.dromara.payment.api;

import lombok.Getter;

@Getter
public class MerchantApiException extends RuntimeException {
    private final int httpStatus;
    private final String code;
    private final boolean retryable;
    private final Long retryAfterSeconds;

    public MerchantApiException(int httpStatus, String code, String message, boolean retryable) {
        this(httpStatus, code, message, retryable, null);
    }

    public MerchantApiException(
        int httpStatus,
        String code,
        String message,
        boolean retryable,
        Long retryAfterSeconds
    ) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.retryable = retryable;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public MerchantApiError toError() {
        return new MerchantApiError(code, getMessage(), retryable, retryAfterSeconds);
    }
}
