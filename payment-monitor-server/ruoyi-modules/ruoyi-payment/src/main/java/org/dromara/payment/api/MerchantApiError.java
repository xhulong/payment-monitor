package org.dromara.payment.api;

public record MerchantApiError(
    String code,
    String message,
    boolean retryable,
    Long retryAfterSeconds
) {
}
