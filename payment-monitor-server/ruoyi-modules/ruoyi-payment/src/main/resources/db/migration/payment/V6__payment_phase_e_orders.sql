create table if not exists pm_qr_asset
(
    id                  bigint primary key,
    merchant_id         bigint        not null references pm_merchant (id),
    platform            varchar(16)   not null,
    asset_name          varchar(100)  not null,
    qr_content_template text          not null,
    status              char(1)       not null default '0',
    remark              varchar(500),
    created_at          timestamptz   not null,
    updated_at          timestamptz   not null,
    constraint chk_pm_qr_asset_platform check (platform in ('WECHAT', 'ALIPAY')),
    constraint chk_pm_qr_asset_status check (status in ('0', '1'))
);

create index if not exists idx_pm_qr_asset_merchant_platform
    on pm_qr_asset (merchant_id, platform, status);

create table if not exists pm_payment_order
(
    id                      bigint primary key,
    merchant_id             bigint       not null references pm_merchant (id),
    merchant_order_no       varchar(64)  not null,
    platform                varchar(16)  not null,
    qr_asset_id             bigint       not null references pm_qr_asset (id),
    requested_amount_minor  bigint       not null,
    payable_amount_minor    bigint       not null,
    amount_offset_minor     integer      not null,
    currency                char(3)      not null default 'CNY',
    status                  varchar(16)  not null,
    public_token            varchar(64)  not null,
    subject                 varchar(200),
    customer_note           varchar(500),
    matched_event_id        bigint references pm_payment_event (id),
    created_at              timestamptz  not null,
    expires_at              timestamptz  not null,
    paid_at                 timestamptz,
    cancelled_at            timestamptz,
    updated_at              timestamptz  not null,
    unique (merchant_id, merchant_order_no),
    unique (public_token),
    unique (matched_event_id),
    constraint chk_pm_order_platform check (platform in ('WECHAT', 'ALIPAY')),
    constraint chk_pm_order_status check (status in ('PENDING', 'PAID', 'EXPIRED', 'CANCELLED', 'CONFLICT')),
    constraint chk_pm_order_amount check (
        requested_amount_minor > 0
        and payable_amount_minor > 0
        and amount_offset_minor between 0 and 99
        and payable_amount_minor = requested_amount_minor + amount_offset_minor
    )
);

create unique index if not exists uk_pm_order_active_amount
    on pm_payment_order (merchant_id, platform, payable_amount_minor)
    where status = 'PENDING';
create index if not exists idx_pm_order_status_expire
    on pm_payment_order (merchant_id, status, expires_at);
create index if not exists idx_pm_order_match_search
    on pm_payment_order (merchant_id, platform, payable_amount_minor, created_at, expires_at);

create table if not exists pm_order_match_audit
(
    id                  bigint primary key,
    merchant_id         bigint       not null references pm_merchant (id),
    order_id            bigint       not null references pm_payment_order (id),
    event_id            bigint references pm_payment_event (id),
    action              varchar(24)  not null,
    before_status       varchar(16),
    after_status        varchar(16),
    note                varchar(500),
    operated_by         bigint,
    operated_at         timestamptz  not null,
    constraint chk_pm_order_audit_action check (
        action in ('CREATE', 'AUTO_MATCH', 'MANUAL_MATCH', 'CONFLICT', 'CANCEL', 'EXPIRE')
    )
);

create index if not exists idx_pm_order_match_audit_order_time
    on pm_order_match_audit (order_id, operated_at desc);

insert into sys_menu values
    (1900100000000000040, '收款二维码', 1900100000000000001, 4, 'qrcode', 'payment/qrcode/index', '', 'N', 'Y', 'C', '0', '0', 'payment:qrcode:list', 'qrcode', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '收款二维码资产'),
    (1900100000000000050, '支付订单', 1900100000000000001, 5, 'order', 'payment/order/index', '', 'N', 'Y', 'C', '0', '0', 'payment:order:list', 'shopping', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '动态金额支付订单'),
    (1900100000000001040, '新增二维码', 1900100000000000040, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:qrcode:add', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001041, '修改二维码', 1900100000000000040, 2, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:qrcode:edit', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001050, '创建支付订单', 1900100000000000050, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:order:add', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001051, '取消支付订单', 1900100000000000050, 2, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:order:cancel', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001052, '人工匹配订单', 1900100000000000050, 3, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:order:match', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '')
on conflict (menu_id) do nothing;
