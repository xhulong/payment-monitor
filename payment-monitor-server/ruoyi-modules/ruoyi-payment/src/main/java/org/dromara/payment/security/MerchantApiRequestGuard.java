package org.dromara.payment.security;

import org.dromara.common.redis.utils.RedisUtils;
import org.redisson.api.RateType;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MerchantApiRequestGuard {

    public boolean claimNonce(String keyId, String nonce, long ttlSeconds) {
        return RedisUtils.setObjectIfAbsent(
            "payment:merchant-api:nonce:" + keyId + ":" + nonce,
            "1",
            Duration.ofSeconds(ttlSeconds));
    }

    public void releaseNonce(String keyId, String nonce) {
        RedisUtils.deleteObject("payment:merchant-api:nonce:" + keyId + ":" + nonce);
    }

    public boolean allowRequest(String keyId, int requestsPerMinute) {
        return RedisUtils.rateLimiter(
            "payment:merchant-api:rate:" + keyId,
            RateType.OVERALL,
            requestsPerMinute,
            60) >= 0;
    }
}
