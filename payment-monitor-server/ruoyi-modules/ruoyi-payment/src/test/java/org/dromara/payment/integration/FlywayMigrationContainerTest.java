package org.dromara.payment.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationContainerTest {

    private static final MigrationVersion ACCOUNT_RECOVERY =
        MigrationVersion.fromVersion("15.3");
    private static final MigrationVersion MAIL_CENTER =
        MigrationVersion.fromVersion("15.10");
    private static final MigrationVersion LATEST =
        MigrationVersion.fromVersion("15.17");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("payment_monitor")
            .withUsername("payment_monitor")
            .withPassword("payment_monitor_test");

    @Test
    void migratesEmptyPaymentSchemaToV15_3ThenLatestAndRemainsIdempotent()
        throws Exception {
        String schema = "empty_to_latest";
        createSchemaAndFrameworkSubstrate(schema);

        Flyway accountRecovery = flyway(schema, ACCOUNT_RECOVERY);
        var first = accountRecovery.migrate();
        var second = accountRecovery.migrate();

        assertEquals(ACCOUNT_RECOVERY.getVersion(), first.targetSchemaVersion);
        assertEquals(18, first.migrationsExecuted);
        assertEquals(0, second.migrationsExecuted);
        assertV15_3Schema(schema);
        assertEquals(19, queryInt(schema,
            "select count(*) from flyway_schema_history where success"));

        Flyway latest = flyway(schema, LATEST);
        var latestMigration = latest.migrate();

        assertEquals(LATEST.getVersion(), latestMigration.targetSchemaVersion);
        assertEquals(14, latestMigration.migrationsExecuted);
        assertEquals(0, latest.migrate().migrationsExecuted);
        assertLatestSchema(schema);
        assertEquals(33, queryInt(schema,
            "select count(*) from flyway_schema_history where success"));
    }

    @Test
    void migratesRepresentativeV1DataToV15_3ThenLatest() throws Exception {
        String schema = "v1_to_latest";
        createSchemaAndFrameworkSubstrate(schema);

        Flyway v1 = flyway(schema, MigrationVersion.fromVersion("1"));
        assertEquals(1, v1.migrate().migrationsExecuted);
        insertRepresentativeV1Data(schema);

        Flyway accountRecovery = flyway(schema, ACCOUNT_RECOVERY);
        var migration = accountRecovery.migrate();

        assertEquals(ACCOUNT_RECOVERY.getVersion(), migration.targetSchemaVersion);
        assertEquals(17, migration.migrationsExecuted);
        assertEquals(0, accountRecovery.migrate().migrationsExecuted);
        assertV15_3Schema(schema);
        assertRepresentativeV1Data(schema);
        insertLegacyWebhookV1(schema);

        Flyway latest = flyway(schema, LATEST);
        var latestMigration = latest.migrate();

        assertEquals(LATEST.getVersion(), latestMigration.targetSchemaVersion);
        assertEquals(14, latestMigration.migrationsExecuted);
        assertEquals(0, latest.migrate().migrationsExecuted);
        assertLatestSchema(schema);
        assertRepresentativeV1Data(schema);
        assertLegacyWebhookUpgraded(schema);
    }

    @Test
    void upgradesExistingMailSenderNameToLuLuPay() throws Exception {
        String schema = "mail_brand_upgrade";
        createSchemaAndFrameworkSubstrate(schema);

        Flyway mailCenter = flyway(schema, MAIL_CENTER);
        assertEquals(MAIL_CENTER.getVersion(), mailCenter.migrate().targetSchemaVersion);
        execute(schema, """
            insert into pm_mail_server_config
            (
                id, enabled, host, port, auth_enabled, username,
                from_name, from_address, security_mode,
                connection_timeout_ms, read_timeout_ms,
                updated_at, version
            )
            values
            (
                1, true, 'smtp.example.test', 465, true, 'mailer',
                '噜噜', 'mailer@example.test', 'SSL',
                5000, 5000, now(), 3
            )
            """);

        Flyway latest = flyway(schema, LATEST);
        assertEquals(7, latest.migrate().migrationsExecuted);
        assertEquals("LuLuPay", queryString(schema, """
            select from_name
            from pm_mail_server_config
            where id = 1
            """));
        assertEquals(4, queryInt(schema, """
            select version
            from pm_mail_server_config
            where id = 1
            """));
    }

    private void assertRepresentativeV1Data(String schema) throws Exception {
        try (Connection connection = connection(schema);
             var statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 select e.client_event_id,
                        e.amount_minor,
                        e.client_received_at_ms,
                        e.client_sent_at_ms,
                        e.device_sequence,
                        m.lifecycle_status,
                        t.status,
                        t.confirmation_status
                 from pm_payment_event e
                 join pm_merchant m on m.id = e.merchant_id
                 join pm_payment_transaction t on t.event_id = e.id
                 where e.id = 9103
                 """)) {
            assertTrue(result.next());
            assertEquals("fixture-event-v1", result.getString("client_event_id"));
            assertEquals(1234L, result.getLong("amount_minor"));
            assertTrue(result.getLong("client_received_at_ms") > 0);
            assertTrue(result.getLong("client_sent_at_ms") > 0);
            assertNull(result.getObject("device_sequence"));
            assertEquals("ACTIVE", result.getString("lifecycle_status"));
            assertEquals("OBSERVED", result.getString("status"));
            assertEquals("UNCONFIRMED", result.getString("confirmation_status"));
        }
    }

    private Flyway flyway(String schema, MigrationVersion target) {
        return Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(schema)
            .defaultSchema(schema)
            .locations("classpath:db/migration/payment")
            .baselineOnMigrate(true)
            .baselineVersion(MigrationVersion.fromVersion("0"))
            .target(target)
            .cleanDisabled(true)
            .load();
    }

    private void createSchemaAndFrameworkSubstrate(String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            statement.execute("""
                create table sys_menu
                (
                    menu_id     bigint primary key,
                    menu_name   varchar(50) not null,
                    parent_id   bigint default 0,
                    order_num   integer default 0,
                    path        varchar(200) default '',
                    component   varchar(255),
                    query_param varchar(255),
                    is_frame    char default 'N',
                    is_cache    char default 'Y',
                    menu_type   char default '',
                    visible     char default '0',
                    status      char default '0',
                    perms       varchar(100),
                    icon        varchar(100) default '#',
                    active_menu varchar(255) default '',
                    ext         varchar(2000) default '',
                    create_dept bigint,
                    create_by   bigint,
                    create_time timestamp,
                    update_by   bigint,
                    update_time timestamp,
                    remark      varchar(500) default ''
                )
                """);
            statement.execute("""
                create table sys_config
                (
                    config_id    bigint primary key,
                    config_name  varchar(100) default '',
                    config_key   varchar(100) default '',
                    config_value varchar(500) default '',
                    config_type  char default 'N',
                    create_dept  bigint,
                    create_by    bigint,
                    create_time  timestamp,
                    update_by    bigint,
                    update_time  timestamp,
                    remark       varchar(500)
                )
                """);
            statement.execute("""
                insert into sys_menu
                (
                    menu_id, menu_name, path, menu_type, visible, status,
                    icon, create_time, remark
                )
                values
                (
                    1761400000000000004, 'PLUS官网',
                    'https://gitee.com/dromara/RuoYi-Vue-Plus',
                    'M', '0', '0', 'guide', now(),
                    'RuoYi-Vue-Plus官网地址'
                )
                """);
            statement.execute("""
                create table sys_role
                (
                    role_id             bigint primary key,
                    role_name           varchar(30) not null,
                    role_key            varchar(100) not null,
                    role_sort           integer not null,
                    data_scope          char default '1',
                    menu_check_strictly boolean default true,
                    dept_check_strictly boolean default true,
                    status              char not null,
                    del_flag            char default '0',
                    create_dept         bigint,
                    create_by           bigint,
                    create_time         timestamp,
                    update_by           bigint,
                    update_time         timestamp,
                    remark              varchar(500)
                )
                """);
            statement.execute("""
                create table sys_role_menu
                (
                    role_id bigint not null,
                    menu_id bigint not null,
                    primary key (role_id, menu_id)
                )
                """);
            statement.execute("""
                create table sys_user
                (
                    user_id      bigint primary key,
                    dept_id      bigint,
                    user_name    varchar(30) not null,
                    nick_name    varchar(30) not null,
                    user_type    varchar(10) default 'sys_user',
                    email        varchar(50) default '',
                    phone_number varchar(11) default '',
                    gender       char default '0',
                    avatar       bigint,
                    password     varchar(100) default '',
                    status       char default '0',
                    del_flag     char default '0',
                    login_ip     varchar(128) default '',
                    login_date   timestamp,
                    create_dept  bigint,
                    create_by    bigint,
                    create_time  timestamp,
                    update_by    bigint,
                    update_time  timestamp,
                    remark       varchar(500)
                )
                """);
        }
    }

    private void insertRepresentativeV1Data(String schema) throws Exception {
        try (Connection connection = connection(schema);
             var statement = connection.createStatement()) {
            statement.execute("""
                insert into pm_merchant (
                    id, merchant_code, name, status, created_at, updated_at
                ) values (
                    9101, 'V1-FIXTURE', 'V1 migration fixture', '0',
                    '2026-07-01 01:02:03+00', '2026-07-01 01:02:03+00'
                )
                """);
            statement.execute("""
                insert into pm_device (
                    id, merchant_id, device_name, android_id_hash,
                    app_version, parser_version, status, paired_at,
                    last_seen_at, last_ip, created_at, updated_at
                ) values (
                    9102, 9101, 'fixture-device', 'fixture-android-hash',
                    '1.0.0', '1', '0', '2026-07-01 01:03:00+00',
                    '2026-07-01 01:04:00+00', '192.0.2.10',
                    '2026-07-01 01:03:00+00', '2026-07-01 01:04:00+00'
                )
                """);
            statement.execute("""
                insert into pm_payment_event (
                    id, merchant_id, device_id, client_event_id, platform,
                    direction, amount_minor, currency, event_time, received_at,
                    parse_status, parser_version, matched_rule, fingerprint,
                    notification_key_hash, raw_hash, raw_payload, status
                ) values (
                    9103, 9101, 9102, 'fixture-event-v1', 'WECHAT',
                    'INCOME', 1234, 'CNY', '2026-07-01 01:05:06.789+00',
                    '2026-07-01 01:05:07+00', 'PARSED', '1',
                    'fixture-income', repeat('a', 64), repeat('b', 64),
                    repeat('c', 64), '{"fixture":true}'::jsonb, 'RECEIVED'
                )
                """);
        }
    }

    private void insertLegacyWebhookV1(String schema) throws Exception {
        try (Connection connection = connection(schema);
             var statement = connection.createStatement()) {
            statement.execute("""
                insert into pm_webhook_endpoint (
                    id, merchant_id, endpoint_name, endpoint_url,
                    secret_ciphertext, status, event_types, platform_filter,
                    payload_version, created_at, updated_at
                ) values (
                    9191, 9101, 'Legacy webhook', 'https://example.test/webhook',
                    'fixture-secret', '0', 'payment.order.paid', 'ALL',
                    1, now(), now()
                ), (
                    9194, 9101, 'Legacy webhook backup', 'https://backup.example.test/webhook',
                    'fixture-secret-backup', '0', 'payment.order.paid', 'ALL',
                    1, now(), now()
                )
                """);
            statement.execute("""
                insert into pm_webhook_outbox (
                    id, delivery_id, event_id, schema_version,
                    merchant_id, endpoint_id, aggregate_type, aggregate_id,
                    event_type, payload, status, attempt_count,
                    next_attempt_at, created_at, updated_at
                ) values (
                    9192, 'legacy-delivery', 'legacy-event', 1,
                    9101, 9191, 'PAYMENT_ORDER', 9193,
                    'payment.order.paid',
                    '{
                      "schemaVersion": 1,
                      "deliveryId": "legacy-delivery",
                      "type": "payment.order.paid",
                      "data": {"orderId": 9193}
                    }'::jsonb,
                    'DELIVERED', 1, now(), now(), now()
                ), (
                    9195, 'legacy-delivery-backup', 'legacy-event-backup', 1,
                    9101, 9194, 'PAYMENT_ORDER', 9193,
                    'payment.order.paid',
                    '{
                      "schemaVersion": 1,
                      "deliveryId": "legacy-delivery-backup",
                      "type": "payment.order.paid",
                      "data": {"orderId": 9193}
                    }'::jsonb,
                    'DELIVERED', 1, now(), now(), now()
                )
                """);
        }
    }

    private void assertLegacyWebhookUpgraded(String schema) throws Exception {
        String eventId = queryString(schema, """
            select event_id
            from pm_webhook_outbox
            where id = 9192
            """);
        assertEquals(2, queryInt(schema, """
            select schema_version
            from pm_webhook_outbox
            where id = 9192
            """));
        assertEquals("2", queryString(schema, """
            select payload ->> 'schemaVersion'
            from pm_webhook_outbox
            where id = 9192
            """));
        assertEquals(eventId, queryString(schema, """
            select payload ->> 'eventId'
            from pm_webhook_outbox
            where id = 9192
            """));
        assertEquals(1, queryInt(schema, """
            select count(distinct event_id)
            from pm_webhook_outbox
            where id in (9192, 9195)
            """));
        assertEquals(0, queryInt(schema, """
            select count(*)
            from pm_webhook_outbox
            where id in (9192, 9195)
              and payload ->> 'eventId' <> event_id
            """));
        assertTrue(eventId.matches(
            "^[0-9a-f]{8}-[0-9a-f]{4}-3[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"));
    }

    private void assertV15_3Schema(String schema) throws Exception {
        assertEquals("15.3", queryString(schema, """
            select version
            from flyway_schema_history
            where success
            order by installed_rank desc
            limit 1
            """));
        assertNotNull(queryString(schema,
            "select to_regclass('pm_refresh_session')::text"));
        assertNotNull(queryString(schema,
            "select to_regclass('pm_account_recovery_challenge')::text"));
        assertNull(queryString(schema,
            "select to_regclass('pm_mail_outbox')::text"));
        assertEquals(1, queryInt(schema, """
            select count(*)
            from information_schema.columns
            where table_schema = current_schema()
              and table_name = 'pm_app_release'
              and column_name = 'verification_status'
            """));
    }

    private void assertLatestSchema(String schema) throws Exception {
        assertEquals("15.17", queryString(schema, """
            select version
            from flyway_schema_history
            where success
            order by installed_rank desc
            limit 1
            """));
        assertTrue(queryString(schema, """
            select pg_get_constraintdef(oid)
            from pg_constraint
            where conname = 'chk_pm_sensitive_operation_verification'
              and conrelid = 'pm_sensitive_operation_log'::regclass
            """).contains("SESSION"));
        assertEquals("SESSION", queryString(schema, """
            select column_default
            from information_schema.columns
            where table_schema = current_schema()
              and table_name = 'pm_sensitive_operation_log'
              and column_name = 'verification_method'
            """).replace("'", "").replace("::character varying", ""));
        assertEquals("true", queryString(schema, """
            select config_value
            from sys_config
            where config_key = 'payment.merchant.onboarding.reviewEnabled'
            """));
        assertEquals(1, queryInt(schema, """
            select count(*)
            from sys_menu
            where menu_id = 1900100000000000112
              and parent_id = 1900100000000000110
              and menu_type = 'F'
              and perms = 'payment:merchant-application:settings'
              and visible = '0'
              and status = '0'
            """));
        assertEquals(0, queryInt(schema, """
            select count(*)
            from sys_role_menu
            where role_id = 1900200000000000003
              and menu_id = 1900100000000000112
            """));
        assertEquals(1, queryInt(schema, """
            select count(*)
            from sys_menu
            where menu_id = 1900100000000001250
              and perms = 'payment:scope:all'
              and visible = '0'
              and status = '0'
            """));
        assertEquals(0, queryInt(schema, """
            select count(*)
            from sys_role_menu
            where menu_id = 1900100000000001250
            """));
        assertTrue(queryString(schema, """
            select pg_get_constraintdef(oid)
            from pg_constraint
            where conname = 'chk_pm_order_audit_action'
              and conrelid = 'pm_order_match_audit'::regclass
            """).contains("FORCE_MATCH"));
        assertEquals("LuLuPay项目", queryString(schema, """
            select menu_name
            from sys_menu
            where menu_id = 1761400000000000004
            """));
        assertEquals("https://github.com/xhulong/payment-monitor-server", queryString(schema, """
            select path
            from sys_menu
            where menu_id = 1761400000000000004
            """));
        assertEquals("LuLuPay项目地址", queryString(schema, """
            select remark
            from sys_menu
            where menu_id = 1761400000000000004
            """));
        assertEquals("账号安全已迁移至个人中心", queryString(schema, """
            select remark
            from sys_menu
            where menu_id = 1900100000000000150
            """));
        assertEquals("1", queryString(schema, """
            select visible
            from sys_menu
            where menu_id = 1900100000000000150
            """));
        assertNotNull(queryString(schema,
            "select to_regclass('pm_mail_outbox')::text"));
        assertNull(queryString(schema,
            "select to_regclass('pm_payment_approval')::text"));
        assertNotNull(queryString(schema,
            "select to_regclass('pm_sensitive_operation_log')::text"));
        assertEquals(0, queryInt(schema, """
            select count(*)
            from information_schema.columns
            where table_schema = current_schema()
              and table_name = 'pm_reconciliation_item'
              and column_name = 'pending_approval_id'
            """));
        assertEquals("敏感操作记录", queryString(schema, """
            select menu_name
            from sys_menu
            where menu_id = 1900100000000000090
            """));
        assertEquals("sensitive-operation", queryString(schema, """
            select path
            from sys_menu
            where menu_id = 1900100000000000090
            """));
        assertEquals("payment:sensitive-operation:list", queryString(schema, """
            select perms
            from sys_menu
            where menu_id = 1900100000000000090
            """));
        assertEquals(0, queryInt(schema, """
            select count(*)
            from sys_menu
            where menu_id = 1900100000000001090
            """));
        assertEquals(0, queryInt(schema, """
            select count(*)
            from sys_role_menu
            where menu_id = 1900100000000001090
            """));
        assertEquals(1, queryInt(schema, """
            select count(*)
            from information_schema.columns
            where table_schema = current_schema()
              and table_name = 'pm_app_release'
                and column_name = 'verification_status'
            """));
        assertEquals(0, queryInt(schema, """
            select count(*)
            from information_schema.columns
            where table_schema = current_schema()
              and table_name = 'pm_webhook_endpoint'
              and column_name = 'payload_version'
            """));
        assertEquals(0, queryInt(schema, """
            select count(*)
            from pm_webhook_outbox
            where schema_version <> 2
               or payload ->> 'schemaVersion' <> '2'
               or payload ->> 'eventId' is null
            """));
        assertNotNull(queryString(schema,
            "select to_regclass('pm_payment_integration')::text"));
        assertNotNull(queryString(schema,
            "select to_regclass('pm_payment_integration_secret')::text"));
        assertNotNull(queryString(schema,
            "select to_regclass('pm_payment_integration_route')::text"));
        assertNotNull(queryString(schema,
            "select to_regclass('pm_external_order_binding')::text"));
        assertNotNull(queryString(schema,
            "select to_regclass('pm_protocol_callback_outbox')::text"));
        assertNotNull(queryString(schema,
            "select to_regclass('pm_protocol_callback_delivery_log')::text"));
        assertNotNull(queryString(schema,
            "select to_regclass('pm_mail_server_config')::text"));
        assertEquals("邮件中心", queryString(schema, """
            select menu_name
            from sys_menu
            where menu_id = 1900100000000000210
            """));
        assertEquals(5, queryInt(schema, """
            select count(distinct perms)
            from sys_menu
            where perms in (
                'system:mail-settings:view',
                'system:mail-settings:edit',
                'system:mail-settings:test',
                'system:mail-outbox:list',
                'system:mail-outbox:retry'
            )
            """));
        assertEquals(1, queryInt(schema, """
            select count(*) from information_schema.columns
            where table_schema = current_schema()
              and table_name = 'pm_external_order_binding'
              and column_name = 'allowed_callback_hosts'
            """));
        assertEquals(8, queryInt(schema, """
            select count(*) from sys_role_menu
            where role_id = 1900200000000000006
              and menu_id in (
                1900100000000000200,
                1900100000000001200,
                1900100000000001201,
                1900100000000001202,
                1900100000000001203,
                1900100000000001204,
                1900100000000001205,
                1900100000000001206
              )
            """));
        assertEquals(3, queryInt(schema, """
            select count(*) from sys_role_menu
            where role_id = 1900200000000000005
              and menu_id in (
                1900100000000000200,
                1900100000000001204,
                1900100000000001205
              )
            """));
        assertEquals(0, queryInt(schema, """
            select count(*) from sys_role_menu
            where role_id in (1900200000000000002, 1900200000000000003)
              and menu_id in (
                1900100000000000200,
                1900100000000001200,
                1900100000000001201,
                1900100000000001202,
                1900100000000001203,
                1900100000000001204,
                1900100000000001205,
                1900100000000001206
              )
            """));
    }

    private Connection connection(String schema) throws Exception {
        Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        connection.setSchema(schema);
        return connection;
    }

    private void execute(String schema, String sql) throws Exception {
        try (Connection connection = connection(schema);
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int queryInt(String schema, String sql) throws Exception {
        try (Connection connection = connection(schema);
             var statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private String queryString(String schema, String sql) throws Exception {
        try (Connection connection = connection(schema);
             var statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
