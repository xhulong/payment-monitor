package org.dromara.common.encrypt.v2.crypto;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class ApiCryptoV2ReplayGuardContainerTest {

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static RedissonClient redissonClient;
    private static ApiCryptoV2ReplayGuard replayGuard;

    @BeforeAll
    static void createClient() {
        Config config = new Config();
        config.useSingleServer().setAddress(
            "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redissonClient = Redisson.create(config);
        replayGuard = new ApiCryptoV2ReplayGuard(redissonClient);
    }

    @AfterAll
    static void closeClient() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    void atomicallyConsumesJtiOnlyOnceUnderConcurrency() throws Exception {
        String jti = UUID.randomUUID().toString();
        int callers = 16;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(callers)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < callers; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return replayGuard.consume(jti, Duration.ofSeconds(30));
                }));
            }
            ready.await();
            start.countDown();

            long accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
            assertEquals(1, accepted);
        }
    }

    @Test
    void allowsJtiAgainOnlyAfterTtlExpires() throws Exception {
        String jti = UUID.randomUUID().toString();

        assertTrue(replayGuard.consume(jti, Duration.ofSeconds(1)));
        assertFalse(replayGuard.consume(jti, Duration.ofSeconds(1)));
        Thread.sleep(1_200);
        assertTrue(replayGuard.consume(jti, Duration.ofSeconds(1)));
    }
}
