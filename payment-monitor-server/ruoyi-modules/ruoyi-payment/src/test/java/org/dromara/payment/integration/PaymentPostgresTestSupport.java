package org.dromara.payment.integration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;

final class PaymentPostgresTestSupport {

    private PaymentPostgresTestSupport() {
    }

    static DataSource migrateLatest(PostgreSQLContainer<?> postgres, String schema)
        throws Exception {
        createSchemaAndFrameworkSubstrate(postgres, schema);
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas(schema)
            .defaultSchema(schema)
            .locations("classpath:db/migration/payment")
            .baselineOnMigrate(true)
            .baselineVersion(MigrationVersion.fromVersion("0"))
            .target(MigrationVersion.fromVersion("15.17"))
            .cleanDisabled(true)
            .load()
            .migrate();

        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        dataSource.setCurrentSchema(schema);
        return dataSource;
    }

    static SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMappers("org.dromara.payment.mapper");
        configuration.addMappers("org.dromara.payment.integration.epay.mapper");

        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(
            new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/payment/*.xml")
        );
        return factory.getObject();
    }

    private static void createSchemaAndFrameworkSubstrate(
        PostgreSQLContainer<?> postgres,
        String schema
    ) throws Exception {
        try (Connection connection = DriverManager.getConnection(
            postgres.getJdbcUrl(),
            postgres.getUsername(),
            postgres.getPassword());
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
            statement.execute("""
                create table sys_user_role
                (
                    user_id bigint not null,
                    role_id bigint not null,
                    primary key (user_id, role_id)
                )
                """);
        }
    }
}
