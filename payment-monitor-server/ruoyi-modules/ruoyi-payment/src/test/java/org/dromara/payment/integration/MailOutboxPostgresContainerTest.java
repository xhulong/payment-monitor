package org.dromara.payment.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class MailOutboxPostgresContainerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("payment_monitor")
            .withUsername("payment_monitor")
            .withPassword("payment_monitor_test");

    @Test
    void migrationAndSkipLockedClaimWorkUnderConcurrency() throws Exception {
        try (Connection setup = connection()) {
            executeMigration(setup);
            insert(setup, 1L, "message-1", "PENDING", null);
            insert(setup, 2L, "message-2", "PENDING", null);
            insert(
                setup,
                3L,
                "message-3",
                "SENDING",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5)
            );
        }

        try (Connection first = connection();
             Connection second = connection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);

            long firstId = claim(first);
            long secondId = claim(second);

            assertNotEquals(firstId, secondId);
            Set<Long> claimed = new HashSet<>(Set.of(firstId, secondId));
            assertEquals(2, claimed.size());

            first.commit();
            second.commit();
        }
        try (Connection third = connection()) {
            third.setAutoCommit(false);
            assertEquals(3L, claim(third));
            third.commit();
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
    }

    private void executeMigration(Connection connection) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(
            "db/migration/payment/V15_4__payment_mail_outbox.sql"
        )) {
            String sql = new String(
                stream.readAllBytes(),
                StandardCharsets.UTF_8
            );
            try (var statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    private void insert(
        Connection connection,
        long id,
        String messageId,
        String status,
        OffsetDateTime lockedAt
    ) throws Exception {
        try (var statement = connection.prepareStatement("""
            insert into pm_mail_outbox (
                id, message_id, message_type, payload_ciphertext,
                encryption_key_id, status, attempt_count, max_attempts,
                next_attempt_at, locked_at, created_at, updated_at
            ) values (?, ?, 'TEST', 'ciphertext', 'key-v1', ?, 0, 3, now(), ?, now(), now())
            """)) {
            statement.setLong(1, id);
            statement.setString(2, messageId);
            statement.setString(3, status);
            if (lockedAt == null) {
                statement.setObject(4, null);
            } else {
                statement.setObject(4, lockedAt);
            }
            statement.executeUpdate();
        }
    }

    private long claim(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("""
            update pm_mail_outbox o
            set status = 'SENDING',
                locked_at = now(),
                updated_at = now()
            from (
                select id
                from pm_mail_outbox
                where (
                    status in ('PENDING', 'RETRYING')
                    and next_attempt_at <= now()
                ) or (
                    status = 'SENDING'
                    and locked_at <= now() - interval '120 seconds'
                )
                order by next_attempt_at asc, id asc
                for update skip locked
                limit 1
            ) due
            where o.id = due.id
            returning o.id
            """);
             var result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }
}
