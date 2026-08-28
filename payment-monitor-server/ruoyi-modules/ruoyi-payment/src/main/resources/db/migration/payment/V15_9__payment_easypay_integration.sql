-- 易支付 EPAY_CLASSIC_V1 接入、外部订单与严格 ACK 回调。
alter table pm_payment_order alter column merchant_order_no type varchar(128);

create table pm_payment_integration
(
    id                       bigint primary key,
    merchant_id              bigint        not null references pm_merchant (id),
    integration_code         varchar(64)   not null,
    integration_name         varchar(100)  not null,
    protocol                 varchar(24)   not null default 'EPAY',
    profile                  varchar(32)   not null default 'EPAY_CLASSIC_V1',
    pid                      varchar(32)   not null,
    status                   char(1)       not null default '0',
    default_expire_seconds   integer       not null default 300,
    notify_method            varchar(8)    not null default 'GET',
    callback_policy          varchar(32)   not null default 'NOTIFICATION_MATCHED',
    allowed_callback_hosts   varchar(2000) not null,
    remark                   varchar(500),
    created_by               bigint,
    created_at               timestamptz   not null,
    updated_at               timestamptz   not null,
    constraint uk_pm_payment_integration_pid unique (pid),
    constraint uk_pm_payment_integration_code unique (merchant_id, integration_code),
    constraint chk_pm_payment_integration_protocol check (protocol = 'EPAY'),
    constraint chk_pm_payment_integration_profile check (profile = 'EPAY_CLASSIC_V1'),
    constraint chk_pm_payment_integration_status check (status in ('0', '1')),
    constraint chk_pm_payment_integration_method check (notify_method in ('GET', 'POST')),
    constraint chk_pm_payment_integration_policy check (
        callback_policy in ('NOTIFICATION_MATCHED', 'MANUAL_CONFIRMED', 'RECONCILED')
    ),
    constraint chk_pm_payment_integration_expire check (default_expire_seconds between 30 and 3600)
);
create index idx_pm_payment_integration_merchant_status
    on pm_payment_integration (merchant_id, status, created_at desc);

create table pm_payment_integration_secret
(
    id                  bigint primary key,
    integration_id      bigint        not null references pm_payment_integration (id),
    secret_version      integer       not null,
    secret_ciphertext   varchar(2048) not null,
    encryption_key_id   varchar(64)   not null,
    status              varchar(16)   not null,
    activated_at        timestamptz   not null,
    retired_at          timestamptz,
    revoked_at          timestamptz,
    created_at          timestamptz   not null,
    constraint uk_pm_payment_integration_secret_version unique (integration_id, secret_version),
    constraint chk_pm_payment_integration_secret_status check (status in ('ACTIVE', 'RETIRED', 'REVOKED'))
);
create unique index uk_pm_payment_integration_secret_active
    on pm_payment_integration_secret (integration_id) where status = 'ACTIVE';

create table pm_payment_integration_route
(
    id                  bigint       primary key,
    integration_id      bigint       not null references pm_payment_integration (id),
    merchant_id         bigint       not null references pm_merchant (id),
    pay_type            varchar(32)  not null,
    platform            varchar(16)  not null,
    qr_asset_id         bigint       not null references pm_qr_asset (id),
    priority            integer      not null default 100,
    status              char(1)      not null default '0',
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null,
    constraint uk_pm_payment_integration_route unique (integration_id, pay_type, qr_asset_id),
    constraint chk_pm_payment_integration_route_type check (pay_type in ('alipay', 'wxpay')),
    constraint chk_pm_payment_integration_route_platform check (platform in ('ALIPAY', 'WECHAT')),
    constraint chk_pm_payment_integration_route_status check (status in ('0', '1')),
    constraint chk_pm_payment_integration_route_priority check (priority between 1 and 9999)
);
create index idx_pm_payment_integration_route_lookup
    on pm_payment_integration_route (integration_id, pay_type, status, priority, id);

create table pm_external_order_binding
(
    id                       bigint        primary key,
    merchant_id              bigint        not null references pm_merchant (id),
    integration_id           bigint        not null references pm_payment_integration (id),
    order_id                 bigint        not null references pm_payment_order (id),
    protocol                 varchar(24)   not null default 'EPAY',
    protocol_profile         varchar(32)   not null,
    external_order_no        varchar(64)   not null,
    gateway_trade_no         varchar(64)   not null,
    pay_type                 varchar(32)   not null,
    request_amount_minor     bigint        not null,
    notify_url               varchar(1000) not null,
    return_url               varchar(1000),
    passthrough_param        varchar(500),
    credential_version       integer       not null,
    notify_method            varchar(8)    not null,
    callback_policy          varchar(32)   not null,
    allowed_callback_hosts   varchar(2000) not null,
    request_fingerprint      varchar(64)   not null,
    request_snapshot         jsonb         not null default '{}'::jsonb,
    risk_status              varchar(24)   not null default 'NORMAL',
    risk_reason              varchar(500),
    created_at               timestamptz   not null,
    updated_at               timestamptz   not null,
    constraint uk_pm_external_order unique (integration_id, external_order_no),
    constraint uk_pm_external_gateway_trade unique (gateway_trade_no),
    constraint uk_pm_external_internal_order unique (order_id),
    constraint chk_pm_external_protocol check (protocol = 'EPAY'),
    constraint chk_pm_external_profile check (protocol_profile = 'EPAY_CLASSIC_V1'),
    constraint chk_pm_external_pay_type check (pay_type in ('alipay', 'wxpay')),
    constraint chk_pm_external_amount check (request_amount_minor > 0),
    constraint chk_pm_external_method check (notify_method in ('GET', 'POST')),
    constraint chk_pm_external_policy check (
        callback_policy in ('NOTIFICATION_MATCHED', 'MANUAL_CONFIRMED', 'RECONCILED')
    ),
    constraint chk_pm_external_risk check (risk_status in ('NORMAL', 'CONFIRMATION_REVOKED'))
);
create index idx_pm_external_order_merchant_time
    on pm_external_order_binding (merchant_id, created_at desc);
create index idx_pm_external_order_integration_time
    on pm_external_order_binding (integration_id, created_at desc);

create table pm_protocol_callback_outbox
(
    id                       bigint        primary key,
    delivery_id              varchar(64)   not null unique,
    event_id                 varchar(64)   not null,
    merchant_id              bigint        not null references pm_merchant (id),
    integration_id           bigint        not null references pm_payment_integration (id),
    binding_id               bigint        not null references pm_external_order_binding (id),
    callback_kind            varchar(32)   not null,
    target_url               varchar(1000) not null,
    request_method           varchar(8)    not null,
    content_type             varchar(64)   not null,
    credential_version       integer       not null,
    unsigned_params          jsonb         not null,
    status                   varchar(16)   not null,
    attempt_count            integer       not null default 0,
    next_attempt_at          timestamptz   not null,
    locked_at                timestamptz,
    delivered_at             timestamptz,
    last_http_status         integer,
    last_response            varchar(4096),
    last_error               varchar(1000),
    strict_acknowledged      boolean       not null default false,
    replay_of_id             bigint references pm_protocol_callback_outbox (id),
    replay_reason            varchar(500),
    created_at               timestamptz   not null,
    updated_at               timestamptz   not null,
    constraint chk_pm_protocol_callback_kind check (callback_kind = 'TRADE_SUCCESS'),
    constraint chk_pm_protocol_callback_method check (request_method in ('GET', 'POST')),
    constraint chk_pm_protocol_callback_status check (
        status in ('PENDING', 'DELIVERING', 'RETRYING', 'DELIVERED', 'DEAD')
    )
);
create unique index uk_pm_protocol_callback_original
    on pm_protocol_callback_outbox (binding_id, callback_kind) where replay_of_id is null;
create index idx_pm_protocol_callback_due
    on pm_protocol_callback_outbox (status, next_attempt_at, id);
create index idx_pm_protocol_callback_merchant_time
    on pm_protocol_callback_outbox (merchant_id, created_at desc);

create table pm_protocol_callback_delivery_log
(
    id                  bigint        primary key,
    outbox_id           bigint        not null references pm_protocol_callback_outbox (id),
    delivery_id         varchar(64)   not null,
    attempt_number      integer       not null,
    request_at          timestamptz   not null,
    response_at         timestamptz,
    duration_ms         bigint,
    http_status         integer,
    response_excerpt    varchar(4096),
    error_message       varchar(1000),
    acknowledged        boolean       not null,
    created_at          timestamptz   not null
);
create index idx_pm_protocol_callback_log_outbox
    on pm_protocol_callback_delivery_log (outbox_id, attempt_number desc);

-- 平台与开发 / 支付接入
insert into sys_menu values
    (1900100000000000200, '支付接入', 1900100000000000190, 2, 'integration', 'payment/integration/index', '', 'N', 'Y', 'C', '0', '0', 'payment:integration:list', 'link', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '易支付接入、路由、外部订单和协议回调'),
    (1900100000000001200, '新增支付接入', 1900100000000000200, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:integration:add', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001201, '修改支付接入', 1900100000000000200, 2, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:integration:edit', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001202, '管理接入密钥', 1900100000000000200, 3, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:integration:secret', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001203, '管理支付路由', 1900100000000000200, 4, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:integration:route', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001204, '查看外部订单', 1900100000000000200, 5, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:external-order:list', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001205, '查看协议回调', 1900100000000000200, 6, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:protocol-callback:list', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001206, '重试协议回调', 1900100000000000200, 7, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:protocol-callback:retry', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '')
on conflict (menu_id) do nothing;

-- 商户管理员、OWNER 和 DEVELOPER 可管理接入；FINANCE 和 VIEWER 只读。
insert into sys_role_menu (role_id, menu_id)
select role_id, menu_id
from unnest(array[
    1900200000000000001::bigint,
    1900200000000000004::bigint,
    1900200000000000006::bigint
]) as roles(role_id)
cross join unnest(array[
    1900100000000000200::bigint, 1900100000000001200::bigint,
    1900100000000001201::bigint, 1900100000000001202::bigint,
    1900100000000001203::bigint, 1900100000000001204::bigint,
    1900100000000001205::bigint, 1900100000000001206::bigint
]) as menus(menu_id)
on conflict (role_id, menu_id) do nothing;

insert into sys_role_menu (role_id, menu_id)
select role_id, menu_id
from unnest(array[
    1900200000000000005::bigint,
    1900200000000000007::bigint
]) as roles(role_id)
cross join unnest(array[
    1900100000000000200::bigint,
    1900100000000001204::bigint,
    1900100000000001205::bigint
]) as menus(menu_id)
on conflict (role_id, menu_id) do nothing;
