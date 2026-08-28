package org.dromara.common.encrypt.v2.crypto;

import org.redisson.api.RedissonClient;

import java.time.Duration;

/**
 * Atomically consumes api-crypto-v2 request identifiers.
 */
public class ApiCryptoV2ReplayGuard {

    private static final String KEY_PREFIX = "api-crypto:v2:jti:";

    private final RedissonClient redissonClient;

    public ApiCryptoV2ReplayGuard(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public boolean consume(String jti, Duration ttl) {
        return redissonClient
            .getBucket(KEY_PREFIX + jti)
            .setIfAbsent(Boolean.TRUE, ttl);
    }
}
