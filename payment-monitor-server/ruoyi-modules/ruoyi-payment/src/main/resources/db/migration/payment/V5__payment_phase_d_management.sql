alter table pm_device
    add column if not exists last_upload_at timestamptz,
    add column if not exists pending_count integer not null default 0,
    add column if not exists retrying_count integer not null default 0,
    add column if not exists rejected_count integer not null default 0,
    add column if not exists last_sync_at timestamptz;

create table if not exists pm_device_heartbeat
(
    id              bigint primary key,
    merchant_id     bigint      not null references pm_merchant (id),
    device_id       bigint      not null references pm_device (id),
    heartbeat_at    timestamptz not null,
    app_version     varchar(32),
    parser_version  varchar(32),
    pending_count   integer     not null default 0,
    retrying_count  integer     not null default 0,
    rejected_count  integer     not null default 0,
    last_sync_at    timestamptz,
    client_ip       varchar(64),
    constraint chk_pm_heartbeat_queue_counts check (
        pending_count >= 0 and retrying_count >= 0 and rejected_count >= 0
    )
);

create index if not exists idx_pm_device_heartbeat_device_time
    on pm_device_heartbeat (device_id, heartbeat_at desc);
create index if not exists idx_pm_device_heartbeat_retention
    on pm_device_heartbeat (heartbeat_at);

alter table pm_payment_event
    add column if not exists reviewed_at timestamptz,
    add column if not exists reviewed_by bigint,
    add column if not exists review_note varchar(500);

create index if not exists idx_pm_event_status_time
    on pm_payment_event (merchant_id, status, received_at desc);

create table if not exists pm_payment_event_review
(
    id                      bigint primary key,
    merchant_id             bigint       not null references pm_merchant (id),
    event_id                bigint       not null references pm_payment_event (id),
    action                  varchar(16)  not null,
    before_status           varchar(32)  not null,
    after_status            varchar(32)  not null,
    before_direction        varchar(16),
    after_direction         varchar(16),
    before_amount_minor     bigint,
    after_amount_minor      bigint,
    note                    varchar(500),
    operated_by             bigint,
    operated_at             timestamptz  not null,
    constraint chk_pm_event_review_action check (action in ('REVIEW', 'CORRECT', 'IGNORE'))
);

create index if not exists idx_pm_event_review_event_time
    on pm_payment_event_review (event_id, operated_at desc);

insert into sys_menu values
    (1900100000000001031, '审核支付事件', 1900100000000000030, 2, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:event:review', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001032, '导出支付事件', 1900100000000000030, 3, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:event:export', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001033, '查看脱敏原文', 1900100000000000030, 4, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:event:raw', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001034, '查看完整原文', 1900100000000000030, 5, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:event:raw:full', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '')
on conflict (menu_id) do nothing;
