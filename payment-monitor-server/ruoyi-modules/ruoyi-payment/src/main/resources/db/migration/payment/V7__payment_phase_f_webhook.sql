create table if not exists pm_webhook_endpoint
(
    id                  bigint primary key,
    merchant_id         bigint        not null references pm_merchant (id),
    endpoint_name       varchar(100)  not null,
    endpoint_url        varchar(1000) not null,
    secret_ciphertext   varchar(1024) not null,
    status              char(1)       not null default '0',
    created_at          timestamptz   not null,
    updated_at          timestamptz   not null,
    constraint chk_pm_webhook_endpoint_status check (status in ('0', '1'))
);

create index if not exists idx_pm_webhook_endpoint_merchant_status
    on pm_webhook_endpoint (merchant_id, status);

create table if not exists pm_webhook_outbox
(
    id                  bigint primary key,
    delivery_id         varchar(64)  not null unique,
    merchant_id         bigint       not null references pm_merchant (id),
    endpoint_id         bigint       not null references pm_webhook_endpoint (id),
    aggregate_type      varchar(32)  not null,
    aggregate_id        bigint       not null,
    event_type          varchar(64)  not null,
    payload             jsonb        not null,
    status              varchar(16)  not null,
    attempt_count       integer      not null default 0,
    next_attempt_at     timestamptz  not null,
    locked_at           timestamptz,
    delivered_at        timestamptz,
    last_http_status    integer,
    last_error          varchar(1000),
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null,
    constraint chk_pm_webhook_outbox_status check (
        status in ('PENDING', 'DELIVERING', 'RETRYING', 'DELIVERED', 'DEAD')
    ),
    unique (endpoint_id, event_type, aggregate_type, aggregate_id)
);

create index if not exists idx_pm_webhook_outbox_due
    on pm_webhook_outbox (status, next_attempt_at);
create index if not exists idx_pm_webhook_outbox_aggregate
    on pm_webhook_outbox (aggregate_type, aggregate_id);

create table if not exists pm_webhook_delivery_log
(
    id                  bigint primary key,
    outbox_id           bigint       not null references pm_webhook_outbox (id),
    delivery_id         varchar(64)  not null,
    attempt_number      integer      not null,
    request_at          timestamptz  not null,
    response_at         timestamptz,
    duration_ms         bigint,
    http_status         integer,
    response_excerpt    varchar(4096),
    error_message       varchar(1000),
    success             boolean      not null,
    created_at          timestamptz  not null
);

create index if not exists idx_pm_webhook_delivery_log_outbox
    on pm_webhook_delivery_log (outbox_id, attempt_number desc);

insert into sys_menu values
    (1900100000000000060, 'Webhook', 1900100000000000001, 6, 'webhook', 'payment/webhook/index', '', 'N', 'Y', 'C', '0', '0', 'payment:webhook:list', 'link', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '可靠支付回调'),
    (1900100000000001060, '新增Webhook', 1900100000000000060, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:webhook:add', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001061, '修改Webhook', 1900100000000000060, 2, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:webhook:edit', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001062, '重试Webhook', 1900100000000000060, 3, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:webhook:retry', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '')
on conflict (menu_id) do nothing;
