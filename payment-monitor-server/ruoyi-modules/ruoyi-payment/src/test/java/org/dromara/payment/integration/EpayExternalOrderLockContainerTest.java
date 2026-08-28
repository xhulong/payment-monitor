package org.dromara.payment.integration;

import org.dromara.payment.integration.epay.mapper.ExternalOrderBindingMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class EpayExternalOrderLockContainerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("payment_monitor")
            .withUsername("payment_monitor")
            .withPassword("payment_monitor_test");

    @Test
    void advisoryLockMapperReturnsScalarValueWithoutMybatisVoidMappingError() throws Exception {
        var dataSource = PaymentPostgresTestSupport.migrateLatest(POSTGRES, "epay_lock");
        var sessionFactory = PaymentPostgresTestSupport.sqlSessionFactory(dataSource);

        try (var session = sessionFactory.openSession()) {
            var mapper = session.getMapper(ExternalOrderBindingMapper.class);

            assertEquals(1, mapper.lockExternalOrder(9001L, "SMOKE_LOCK_001"));
        }
    }
}
