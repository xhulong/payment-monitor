package org.dromara.payment.integration;

import org.dromara.payment.mapper.AccountRecoveryChallengeMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class AccountRecoveryChallengeLockContainerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("payment_monitor")
            .withUsername("payment_monitor")
            .withPassword("payment_monitor_test");

    @Test
    void advisoryLockMapperReturnsScalarValueWithoutMybatisVoidMappingError()
        throws Exception {
        var dataSource = PaymentPostgresTestSupport.migrateLatest(
            POSTGRES,
            "account_recovery_lock"
        );
        var sessionFactory =
            PaymentPostgresTestSupport.sqlSessionFactory(dataSource);

        try (var session = sessionFactory.openSession()) {
            var mapper =
                session.getMapper(AccountRecoveryChallengeMapper.class);

            assertEquals(1, mapper.lockIssueScope("EMAIL_CHANGE:100"));
        }
    }
}
