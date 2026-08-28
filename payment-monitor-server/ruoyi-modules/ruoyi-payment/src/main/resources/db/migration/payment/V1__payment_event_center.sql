create table if not exists pm_merchant
(
    id            bigint primary key,
    merchant_code varchar(64)  not null unique,
    name          varchar(100) not null,
    status        char(1)      not null default '0',
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    constraint chk_pm_merchant_status check (status in ('0', '1'))
);

create table if not exists pm_device
(
    id              bigint primary key,
    merchant_id     bigint       not null references pm_merchant (id),
    device_name     varchar(100) not null,
    android_id_hash varchar(128),
    app_version     varchar(32),
    parser_version  varchar(32),
    status          char(1)      not null default '0',
    paired_at       timestamptz  not null,
    last_seen_at    timestamptz,
    last_ip         varchar(64),
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    constraint chk_pm_device_status check (status in ('0', '1'))
);

create index if not exists idx_pm_device_merchant on pm_device (merchant_id);
create index if not exists idx_pm_device_last_seen on pm_device (last_seen_at);

create table if not exists pm_device_credential
(
    id                bigint primary key,
    device_id         bigint       not null references pm_device (id),
    secret_ciphertext varchar(1024) not null,
    key_version       integer      not null,
    created_at        timestamptz  not null default now(),
    revoked_at        timestamptz,
    unique (device_id, key_version)
);

create unique index if not exists uk_pm_device_credential_active
    on pm_device_credential (device_id) where revoked_at is null;

create table if not exists pm_pairing_code
(
    id                bigint primary key,
    merchant_id       bigint      not null references pm_merchant (id),
    code_hash         varchar(64) not null unique,
    expires_at        timestamptz not null,
    used_at           timestamptz,
    used_by_device_id bigint references pm_device (id),
    created_by        bigint,
    created_at        timestamptz not null default now()
);

create index if not exists idx_pm_pairing_code_expire on pm_pairing_code (expires_at);

create table if not exists pm_payment_event
(
    id                    bigint primary key,
    merchant_id           bigint       not null references pm_merchant (id),
    device_id             bigint       not null references pm_device (id),
    client_event_id       varchar(64)  not null,
    platform              varchar(16)  not null,
    direction             varchar(16)  not null,
    amount_minor          bigint,
    currency              char(3)      not null default 'CNY',
    event_time            timestamptz,
    received_at           timestamptz  not null default now(),
    parse_status          varchar(32)  not null,
    parser_version        varchar(32),
    matched_rule          varchar(255),
    fingerprint           varchar(64)  not null,
    notification_key_hash varchar(64),
    raw_hash              varchar(64),
    raw_payload           jsonb,
    status                varchar(32)  not null,
    unique (merchant_id, client_event_id),
    constraint chk_pm_event_platform check (platform in ('WECHAT', 'ALIPAY')),
    constraint chk_pm_event_direction check (direction in ('INCOME', 'EXPENSE', 'UNKNOWN')),
    constraint chk_pm_event_parse_status check (parse_status in ('PARSED', 'AMOUNT_NOT_FOUND', 'AMBIGUOUS')),
    constraint chk_pm_event_amount check (amount_minor is null or amount_minor > 0)
);

create index if not exists idx_pm_event_search
    on pm_payment_event (merchant_id, platform, amount_minor, received_at desc);
create index if not exists idx_pm_event_device
    on pm_payment_event (device_id, received_at desc);
create index if not exists idx_pm_event_parse_status
    on pm_payment_event (parse_status, received_at desc);

insert into pm_merchant (id, merchant_code, name, status)
values (1900000000000000001, 'DEFAULT', '默认商户', '0')
on conflict (id) do nothing;

insert into sys_menu values
    (1900100000000000001, '支付监控', 0, 2, 'payment', null, '', 'N', 'Y', 'M', '0', '0', '', 'money', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '支付通知监控目录'),
    (1900100000000000010, '监控概览', 1900100000000000001, 1, 'dashboard', 'payment/dashboard/index', '', 'N', 'Y', 'C', '0', '0', 'payment:dashboard:view', 'dashboard', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '支付监控概览'),
    (1900100000000000020, '设备管理', 1900100000000000001, 2, 'device', 'payment/device/index', '', 'N', 'Y', 'C', '0', '0', 'payment:device:list', 'mobile', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '支付监控设备'),
    (1900100000000000030, '支付事件', 1900100000000000001, 3, 'event', 'payment/event/index', '', 'N', 'Y', 'C', '0', '0', 'payment:event:list', 'list', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '支付通知事件'),
    (1900100000000001020, '生成配对码', 1900100000000000020, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:device:pair', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001021, '修改设备', 1900100000000000020, 2, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:device:edit', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001030, '事件详情', 1900100000000000030, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:event:query', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '')
on conflict (menu_id) do nothing;
