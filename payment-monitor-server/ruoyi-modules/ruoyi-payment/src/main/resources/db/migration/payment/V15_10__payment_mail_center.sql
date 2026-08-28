create table pm_mail_server_config
(
    id                      bigint primary key,
    enabled                 boolean      not null default false,
    host                    varchar(255) not null,
    port                    integer      not null,
    auth_enabled            boolean      not null default true,
    username                varchar(255),
    password_ciphertext     varchar(2048),
    encryption_key_id       varchar(64),
    from_name               varchar(100) not null,
    from_address            varchar(255) not null,
    security_mode           varchar(16)  not null,
    connection_timeout_ms   bigint       not null default 10000,
    read_timeout_ms         bigint       not null default 10000,
    updated_by              bigint,
    updated_at              timestamptz  not null,
    version                 integer      not null default 0,
    constraint chk_pm_mail_server_config_singleton check (id = 1),
    constraint chk_pm_mail_server_config_port check (port between 1 and 65535),
    constraint chk_pm_mail_server_config_security check (
        security_mode in ('SSL', 'STARTTLS', 'NONE')
    ),
    constraint chk_pm_mail_server_config_timeout check (
        connection_timeout_ms between 1000 and 120000
        and read_timeout_ms between 1000 and 120000
    ),
    constraint chk_pm_mail_server_config_password check (
        (password_ciphertext is null and encryption_key_id is null)
        or (password_ciphertext is not null and encryption_key_id is not null)
    )
);

insert into sys_menu values
    (1900100000000000210, '邮件中心', 1761400000000000001, 12, 'mail', null, '', 'N', 'Y', 'M', '0', '0', '', 'email', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '全平台 SMTP 配置与邮件发送记录'),
    (1900100000000000211, '邮件设置', 1900100000000000210, 1, 'settings', 'system/mail/settings/index', '', 'N', 'Y', 'C', '0', '0', 'system:mail-settings:view', 'setting', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '配置全平台 SMTP 发件服务'),
    (1900100000000000212, '发送记录', 1900100000000000210, 2, 'outbox', 'system/mail/outbox/index', '', 'N', 'Y', 'C', '0', '0', 'system:mail-outbox:list', 'list', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '查看邮件 Outbox 状态与失败原因'),
    (1900100000000001210, '查看邮件设置', 1900100000000000211, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'system:mail-settings:view', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001211, '修改邮件设置', 1900100000000000211, 2, '#', '', '', 'N', 'Y', 'F', '0', '0', 'system:mail-settings:edit', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001212, '测试邮件设置', 1900100000000000211, 3, '#', '', '', 'N', 'Y', 'F', '0', '0', 'system:mail-settings:test', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001213, '查看发送记录', 1900100000000000212, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'system:mail-outbox:list', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001214, '重试发送邮件', 1900100000000000212, 2, '#', '', '', 'N', 'Y', 'F', '0', '0', 'system:mail-outbox:retry', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '')
on conflict (menu_id) do nothing;

insert into sys_role_menu (role_id, menu_id)
select roles.role_id, menus.menu_id
from unnest(array[
    1761300000000000001::bigint,
    1900200000000000001::bigint
]) roles(role_id)
cross join unnest(array[
    1900100000000000210::bigint,
    1900100000000000211::bigint,
    1900100000000000212::bigint,
    1900100000000001210::bigint,
    1900100000000001211::bigint,
    1900100000000001212::bigint,
    1900100000000001213::bigint,
    1900100000000001214::bigint
]) menus(menu_id)
where exists (select 1 from sys_role where role_id = roles.role_id)
on conflict (role_id, menu_id) do nothing;
