package org.dromara.system.service;

import org.dromara.system.domain.StepUpGrant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.CompositeCodec;
import org.redisson.codec.TypedJsonJackson3Codec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class StepUpTokenStoreContainerTest {

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static RedissonClient redissonClient;
    private static StepUpTokenStore tokenStore;

    @BeforeAll
    static void createClient() {
        Config config = new Config();
        JsonMapper jsonMapper = JsonMapper.builder()
            .activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                    .allowIfSubType((context, type) -> true)
                    .build(),
                DefaultTyping.NON_FINAL
            )
            .build();
        TypedJsonJackson3Codec jsonCodec =
            new TypedJsonJackson3Codec(Object.class, jsonMapper);
        config.setCodec(
            new CompositeCodec(
                StringCodec.INSTANCE,
                jsonCodec,
                jsonCodec
            )
        );
        config.useSingleServer().setAddress(
            "redis://" + REDIS.getHost() + ":"
                + REDIS.getMappedPort(6379)
        );
        redissonClient = Redisson.create(config);
        tokenStore = new StepUpTokenStore(redissonClient);
    }

    @AfterAll
    static void closeClient() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    void consumesMatchingTokenExactlyOnce() {
        String token = token();
        StepUpGrant grant = new StepUpGrant(
            100L,
            "session-a",
            "EMAIL_CHANGE"
        );
        tokenStore.issue(token, grant, Duration.ofSeconds(30));

        assertTrue(tokenStore.consume(token, grant));
        assertFalse(tokenStore.consume(token, grant));
    }

    @Test
    void mismatchDoesNotDestroyValidToken() {
        String token = token();
        StepUpGrant grant = new StepUpGrant(
            101L,
            "session-b",
            "PASSWORD_CHANGE"
        );
        tokenStore.issue(token, grant, Duration.ofSeconds(30));

        assertFalse(tokenStore.consume(
            token,
            new StepUpGrant(
                101L,
                "session-b",
                "EMAIL_CHANGE"
            )
        ));
        assertTrue(tokenStore.consume(token, grant));
    }

    @Test
    void crossUserAndCrossSessionTokensAreRejectedWithoutConsumption() {
        String token = token();
        StepUpGrant grant = new StepUpGrant(
            104L,
            "session-e",
            "PAYMENT_ORDER_FORCE_MATCH"
        );
        tokenStore.issue(token, grant, Duration.ofSeconds(30));

        assertFalse(tokenStore.consume(
            token,
            new StepUpGrant(
                105L,
                "session-e",
                "PAYMENT_ORDER_FORCE_MATCH"
            )
        ));
        assertFalse(tokenStore.consume(
            token,
            new StepUpGrant(
                104L,
                "session-f",
                "PAYMENT_ORDER_FORCE_MATCH"
            )
        ));
        assertTrue(tokenStore.consume(token, grant));
    }

    @Test
    void revokesAllIndexedTokensForUser() {
        Long userId = 102L;
        StepUpGrant first = new StepUpGrant(
            userId,
            "session-c",
            "OPERATION_A"
        );
        StepUpGrant second = new StepUpGrant(
            userId,
            "session-c",
            "OPERATION_B"
        );
        String firstToken = token();
        String secondToken = token();
        tokenStore.issue(firstToken, first, Duration.ofSeconds(30));
        tokenStore.issue(secondToken, second, Duration.ofSeconds(30));

        assertEquals(2, tokenStore.revokeAll(userId));
        assertFalse(tokenStore.consume(firstToken, first));
        assertFalse(tokenStore.consume(secondToken, second));
        assertEquals(0, tokenStore.revokeAll(userId));
    }

    @Test
    void tokenAndIndexExpireTogether() throws Exception {
        String token = token();
        StepUpGrant grant = new StepUpGrant(
            103L,
            "session-d",
            "SHORT_OPERATION"
        );
        tokenStore.issue(token, grant, Duration.ofSeconds(1));

        Thread.sleep(1_200);

        assertFalse(tokenStore.consume(token, grant));
        assertEquals(0, tokenStore.revokeAll(grant.userId()));
    }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
