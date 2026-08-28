-- Phase H: role-aware home dashboard, device health, webhook subscriptions,
-- merchant API audit and reconciliation.

alter table pm_device
    add column if not exists monitoring_enabled boolean not null default false,
    add column if not exists listener_connected boolean not null default false,
    add column if not exists foreground_running boolean not null default false,
    add column if not exists notification_access_granted boolean not null default false,
    add column if not exists battery_optimization_ignored boolean not null default false,
    add column if not exists last_notification_at timestamptz,
    add column if not exists last_health_issue varchar(64),
    add column if not exists health_updated_at timestamptz;

alter table pm_device_heartbeat
    add column if not exists monitoring_enabled boolean not null default false,
    add column if not exists listener_connected boolean not null default false,
    add column if not exists foreground_running boolean not null default false,
    add column if not exists notification_access_granted boolean not null default false,
    add column if not exists battery_optimization_ignored boolean not null default false,
    add column if not exists last_notification_at timestamptz,
    add column if not exists health_issue varchar(64);

create index if not exists idx_pm_device_health
    on pm_device (
        merchant_id,
        status,
        monitoring_enabled,
        listener_connected,
        foreground_running,
        last_seen_at desc
    );

alter table pm_webhook_endpoint
    add column if not exists event_types varchar(500)
        not null default 'payment.order.paid',
    add column if not exists platform_filter varchar(16)
        not null default 'ALL';

alter table pm_webhook_endpoint
    drop constraint if exists chk_pm_webhook_platform_filter;

alter table pm_webhook_endpoint
    add constraint chk_pm_webhook_platform_filter
        check (platform_filter in ('ALL', 'WECHAT', 'ALIPAY'));

alter table pm_webhook_outbox
    add column if not exists replay_of_delivery_id varchar(64),
    add column if not exists replay_reason varchar(500);

do $$
declare
    constraint_name text;
begin
    select conname
    into constraint_name
    from pg_constraint
    where conrelid = 'pm_webhook_outbox'::regclass
      and contype = 'u'
      and pg_get_constraintdef(oid) like '%endpoint_id%'
      and pg_get_constraintdef(oid) like '%event_type%'
      and pg_get_constraintdef(oid) like '%aggregate_type%'
      and pg_get_constraintdef(oid) like '%aggregate_id%'
    limit 1;
    if constraint_name is not null then
        execute format(
            'alter table pm_webhook_outbox drop constraint %I',
            constraint_name
        );
    end if;
end
$$;

create unique index if not exists uk_pm_webhook_outbox_original
    on pm_webhook_outbox (
        endpoint_id,
        event_type,
        aggregate_type,
        aggregate_id
    )
    where replay_of_delivery_id is null;

create index if not exists idx_pm_webhook_replay
    on pm_webhook_outbox (merchant_id, replay_of_delivery_id)
    where replay_of_delivery_id is not null;

create table if not exists pm_merchant_api_audit
(
    id              bigint primary key,
    merchant_id     bigint references pm_merchant (id),
    api_key_id      bigint references pm_merchant_api_key (id),
    key_id          varchar(64),
    request_method  varchar(16)  not null,
    request_path    varchar(500) not null,
    client_ip       varchar(64),
    http_status     integer      not null,
    result_code     varchar(64)  not null,
    success         boolean      not null,
    duration_ms     bigint       not null,
    created_at      timestamptz  not null
);

create index if not exists idx_pm_merchant_api_audit_merchant_time
    on pm_merchant_api_audit (merchant_id, created_at desc);

create index if not exists idx_pm_merchant_api_audit_key_time
    on pm_merchant_api_audit (api_key_id, created_at desc);

create table if not exists pm_reconciliation_run
(
    id                         bigint primary key,
    merchant_id                bigint      not null references pm_merchant (id),
    business_date              date        not null,
    timezone                   varchar(64) not null,
    status                     varchar(16) not null,
    paid_order_count           bigint      not null default 0,
    paid_order_amount_minor    bigint      not null default 0,
    matched_income_count       bigint      not null default 0,
    matched_income_amount_minor bigint     not null default 0,
    unmatched_income_count     bigint      not null default 0,
    unmatched_income_amount_minor bigint   not null default 0,
    conflict_order_count       bigint      not null default 0,
    suspected_duplicate_count  bigint      not null default 0,
    webhook_dead_count         bigint      not null default 0,
    amount_difference_minor    bigint      not null default 0,
    created_by                 bigint,
    created_at                 timestamptz not null,
    completed_at               timestamptz not null,
    constraint chk_pm_reconciliation_status
        check (status in ('BALANCED', 'ATTENTION_REQUIRED'))
);

create unique index if not exists uk_pm_reconciliation_merchant_date
    on pm_reconciliation_run (merchant_id, business_date);

create index if not exists idx_pm_reconciliation_merchant_time
    on pm_reconciliation_run (merchant_id, business_date desc);

-- The payment dashboard is integrated into the system home page in Phase H.
update sys_menu
set visible = '1',
    update_time = now(),
    remark = '已集成到管理后台首页'
where menu_id = 1900100000000000010;
