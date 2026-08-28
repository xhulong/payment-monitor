package org.dromara.system.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.domain.StepUpGrant;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RSetCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.codec.TypedJsonJackson3Codec;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class StepUpTokenStore {

    private static final String TOKEN_PREFIX = "payment:step-up:token:";
    private static final String USER_INDEX_PREFIX = "payment:step-up:user:";
    private static final String LOCK_SUFFIX = ":consume-lock";
    private static final Codec GRANT_CODEC =
        new TypedJsonJackson3Codec(StepUpGrant.class);
    private static final Codec TOKEN_CODEC =
        new TypedJsonJackson3Codec(String.class);

    private final RedissonClient redissonClient;

    public void issue(String token, StepUpGrant grant, Duration ttl) {
        if (token == null
            || token.isBlank()
            || grant == null
            || grant.userId() == null
            || grant.sessionToken() == null
            || grant.sessionToken().isBlank()
            || grant.operation() == null
            || grant.operation().isBlank()
            || ttl == null
            || ttl.isZero()
            || ttl.isNegative()) {
            throw new ServiceException("Step-Up Token 上下文无效");
        }
        RBucket<StepUpGrant> bucket =
            redissonClient.getBucket(tokenKey(token), GRANT_CODEC);
        RSetCache<String> index =
            redissonClient.getSetCache(
                userIndexKey(grant.userId()),
                TOKEN_CODEC
            );
        bucket.set(grant, ttl);
        try {
            boolean indexed = index.add(
                token,
                ttl.toMillis(),
                TimeUnit.MILLISECONDS
            );
            if (!indexed) {
                bucket.delete();
                throw new ServiceException("Step-Up Token 索引冲突");
            }
        } catch (RuntimeException e) {
            bucket.delete();
            throw e;
        }
    }

    public boolean consume(String token, StepUpGrant expected) {
        if (token == null || token.isBlank() || expected == null) {
            return false;
        }
        String tokenKey = tokenKey(token);
        RLock lock = redissonClient.getLock(tokenKey + LOCK_SUFFIX);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (!locked) {
                return false;
            }
            RBucket<StepUpGrant> bucket =
                redissonClient.getBucket(tokenKey, GRANT_CODEC);
            StepUpGrant current = bucket.get();
            if (!expected.equals(current)) {
                return false;
            }
            if (!bucket.delete()) {
                return false;
            }
            redissonClient
                .getSetCache(
                    userIndexKey(expected.userId()),
                    TOKEN_CODEC
                )
                .remove(token);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public int revokeAll(Long userId) {
        if (userId == null) {
            return 0;
        }
        RSetCache<String> index =
            redissonClient.getSetCache(userIndexKey(userId), TOKEN_CODEC);
        Set<String> tokens = index.readAll();
        int revoked = 0;
        for (String token : tokens) {
            if (redissonClient
                .getBucket(tokenKey(token), GRANT_CODEC)
                .delete()) {
                revoked++;
            }
        }
        index.delete();
        return revoked;
    }

    private String tokenKey(String token) {
        return TOKEN_PREFIX + token;
    }

    private String userIndexKey(Long userId) {
        return USER_INDEX_PREFIX + userId;
    }
}
