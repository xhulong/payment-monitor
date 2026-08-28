-- Phase J: payment transactions, confirmation levels, amount-slot cooling,
-- dual approval, versioned reconciliation, webhook v2 and device sequences.

alter table pm_payment_event
    add column if not exists device_sequence bigint;

create unique index if not exists uk_pm_event_device_sequence
    on pm_payment_event (device_id, device_sequence)
    where device_sequence is not null;

alter table pm_payment_order
    add column if not exists transaction_id bigint,
    add column if not exists confirmation_status varchar(16) not null default 'UNCONFIRMED',
    add column if not exists confirmed_at timestamptz,
    add column if not exists confirmed_by bigint,
    add column if not exists confirmation_source varchar(32),
    add column if not exists confirmation_note varchar(500),
    add column if not exists version integer not null default 0;

alter table pm_payment_order
    drop constraint if exists chk_pm_order_confirmation_status;

alter table pm_payment_order
    add constraint chk_pm_order_confirmation_status
        check (confirmation_status in ('UNCONFIRMED', 'NOTIFICATION', 'MANUAL', 'RECONCILED'));

create table if not exists pm_payment_transaction
(
    id                    bigint primary key,
    merchant_id           bigint       not null references pm_merchant (id),
    event_id              bigint       not null references pm_payment_event (id),
    order_id              bigint references pm_payment_order (id),
    platform              varchar(16)  not null,
    amount_minor          bigint       not null,
    currency              char(3)      not null default 'CNY',
    status                varchar(16)  not null,
    confirmation_status   varchar(16)  not null default 'UNCONFIRMED',
    observed_at           timestamptz  not null,
    matched_at            timestamptz,
    confirmed_at          timestamptz,
    confirmed_by          bigint,
    reconciled_at         timestamptz,
    reversed_at           timestamptz,
    reversed_by           bigint,
    rejection_reason      varchar(500),
    version               integer      not null default 0,
    created_at            timestamptz  not null,
    updated_at            timestamptz  not null,
    unique (event_id),
    constraint chk_pm_transaction_platform check (platform in ('WECHAT', 'ALIPAY')),
    constraint chk_pm_transaction_status check (
        status in ('OBSERVED', 'MATCHED', 'CONFIRMED', 'RECONCILED', 'REJECTED', 'REVERSED')
    ),
    constraint chk_pm_transaction_confirmation check (
        confirmation_status in ('UNCONFIRMED', 'NOTIFICATION', 'MANUAL', 'RECONCILED')
    )
);

create index if not exists idx_pm_transaction_merchant_status_time
    on pm_payment_transaction (merchant_id, status, observed_at desc);
create index if not exists idx_pm_transaction_order
    on pm_payment_transaction (merchant_id, order_id);

alter table pm_payment_order
    drop constraint if exists fk_pm_order_transaction;
alter table pm_payment_order
    add constraint fk_pm_order_transaction
        foreign key (transaction_id) references pm_payment_transaction (id);

create table if not exists pm_amount_slot_reservation
(
    id                    bigint primary key,
    merchant_id           bigint      not null references pm_merchant (id),
    platform              varchar(16) not null,
    payable_amount_minor  bigint      not null,
    order_id              bigint      not null references pm_payment_order (id),
    status                varchar(16) not null,
    reserved_at           timestamptz not null,
    cooling_until         timestamptz,
    released_at           timestamptz,
    version               integer     not null default 0,
    created_at            timestamptz not null,
    updated_at            timestamptz not null,
    unique (merchant_id, platform, payable_amount_minor),
    constraint chk_pm_amount_slot_platform check (platform in ('WECHAT', 'ALIPAY')),
    constraint chk_pm_amount_slot_status check (status in ('ACTIVE', 'COOLING', 'RELEASED'))
);

create index if not exists idx_pm_amount_slot_order
    on pm_amount_slot_reservation (merchant_id, order_id);
create index if not exists idx_pm_amount_slot_cooling
    on pm_amount_slot_reservation (status, cooling_until);

create table if not exists pm_payment_approval
(
    id                  bigint primary key,
    merchant_id         bigint       not null references pm_merchant (id),
    approval_type       varchar(32)  not null,
    target_type         varchar(32)  not null,
    target_id           bigint       not null,
    status              varchar(16)  not null,
    request_payload     jsonb        not null default '{}'::jsonb,
    before_snapshot     jsonb,
    after_snapshot      jsonb,
    reason              varchar(500),
    requested_by        bigint       not null,
    requested_at        timestamptz  not null,
    reviewed_by         bigint,
    reviewed_at         timestamptz,
    review_note         varchar(500),
    executed_at         timestamptz,
    idempotency_key     varchar(128) not null,
    version             integer      not null default 0,
    unique (merchant_id, idempotency_key),
    constraint chk_pm_approval_type check (
        approval_type in ('FORCE_MATCH', 'REVERSE_CONFIRMATION', 'REASSIGN_FUNDS', 'RECONCILIATION_RESOLUTION')
    ),
    constraint chk_pm_approval_status check (
        status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')
    )
);

create index if not exists idx_pm_approval_merchant_status_time
    on pm_payment_approval (merchant_id, status, requested_at desc);

alter table pm_reconciliation_run
    add column if not exists run_no varchar(64),
    add column if not exists open_difference_count bigint not null default 0,
    add column if not exists resolved_difference_count bigint not null default 0,
    add column if not exists version integer not null default 0;

update pm_reconciliation_run
set run_no = 'LEGACY-' || id::text
where run_no is null or btrim(run_no) = '';

alter table pm_reconciliation_run
    alter column run_no set not null;

drop index if exists uk_pm_reconciliation_merchant_date;
create unique index if not exists uk_pm_reconciliation_run_no
    on pm_reconciliation_run (merchant_id, run_no);
create index if not exists idx_pm_reconciliation_runs_date
    on pm_reconciliation_run (merchant_id, business_date desc, created_at desc);

create table if not exists pm_reconciliation_item
(
    id                  bigint primary key,
    merchant_id         bigint       not null references pm_merchant (id),
    run_id              bigint       not null references pm_reconciliation_run (id),
    difference_type     varchar(40)  not null,
    status              varchar(24)  not null,
    order_id            bigint references pm_payment_order (id),
    event_id            bigint references pm_payment_event (id),
    transaction_id      bigint references pm_payment_transaction (id),
    webhook_outbox_id   bigint references pm_webhook_outbox (id),
    amount_minor        bigint,
    description         varchar(1000),
    resolution_action   varchar(64),
    resolution_note     varchar(500),
    pending_approval_id bigint references pm_payment_approval (id),
    resolved_by         bigint,
    resolved_at         timestamptz,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    constraint chk_pm_reconciliation_item_type check (
        difference_type in (
            'UNMATCHED_INCOME',
            'NOTIFICATION_UNCONFIRMED',
            'AMOUNT_MISMATCH',
            'CONFLICT_ORDER',
            'SUSPECTED_DUPLICATE',
            'DEAD_WEBHOOK',
            'LATE_PAYMENT'
        )
    ),
    constraint chk_pm_reconciliation_item_status check (
        status in ('OPEN', 'PENDING_APPROVAL', 'RESOLVED', 'IGNORED')
    )
);

create index if not exists idx_pm_reconciliation_item_run
    on pm_reconciliation_item (merchant_id, run_id, status, difference_type);

alter table pm_webhook_endpoint
    add column if not exists payload_version integer not null default 1;

alter table pm_webhook_endpoint
    drop constraint if exists chk_pm_webhook_payload_version;
alter table pm_webhook_endpoint
    add constraint chk_pm_webhook_payload_version check (payload_version in (1, 2));

alter table pm_webhook_outbox
    add column if not exists event_id varchar(64),
    add column if not exists schema_version integer not null default 1;

update pm_webhook_outbox
set event_id = coalesce(event_id, delivery_id)
where event_id is null;

alter table pm_webhook_outbox
    alter column event_id set not null;

create index if not exists idx_pm_webhook_business_event
    on pm_webhook_outbox (merchant_id, event_id);

create table if not exists pm_device_assignment
(
    id          bigint primary key,
    merchant_id bigint      not null references pm_merchant (id),
    platform    varchar(16) not null,
    device_id   bigint      not null references pm_device (id),
    role        varchar(16) not null,
    priority    integer     not null default 100,
    enabled     boolean     not null default true,
    created_by  bigint,
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    unique (merchant_id, platform, device_id),
    constraint chk_pm_device_assignment_platform check (platform in ('WECHAT', 'ALIPAY')),
    constraint chk_pm_device_assignment_role check (role in ('PRIMARY', 'BACKUP')),
    constraint chk_pm_device_assignment_priority check (priority between 1 and 9999)
);

create unique index if not exists uk_pm_device_assignment_primary
    on pm_device_assignment (merchant_id, platform)
    where role = 'PRIMARY' and enabled;

create index if not exists idx_pm_device_assignment_active
    on pm_device_assignment (merchant_id, platform, enabled, role, priority);

-- Backfill a transaction for every historical valid income notification.
insert into pm_payment_transaction (
    id, merchant_id, event_id, order_id, platform, amount_minor, currency,
    status, confirmation_status, observed_at, matched_at, created_at, updated_at
)
select
    e.id,
    e.merchant_id,
    e.id,
    o.id,
    e.platform,
    e.amount_minor,
    coalesce(e.currency, 'CNY'),
    case when o.id is not null then 'MATCHED' else 'OBSERVED' end,
    case when o.id is not null then 'NOTIFICATION' else 'UNCONFIRMED' end,
    coalesce(e.event_time, e.received_at),
    case when o.id is not null then coalesce(o.paid_at, e.received_at) end,
    e.received_at,
    greatest(e.received_at, coalesce(o.updated_at, e.received_at))
from pm_payment_event e
left join pm_payment_order o
    on o.merchant_id = e.merchant_id
   and o.matched_event_id = e.id
where e.direction = 'INCOME'
  and e.amount_minor is not null
on conflict (event_id) do nothing;

update pm_payment_order o
set transaction_id = t.id,
    confirmation_status = case when o.status = 'PAID' then 'NOTIFICATION' else o.confirmation_status end,
    confirmation_source = case when o.status = 'PAID' then 'PAYMENT_NOTIFICATION' else o.confirmation_source end,
    confirmed_at = case when o.status = 'PAID' then coalesce(o.paid_at, o.updated_at) else o.confirmed_at end
from pm_payment_transaction t
where t.order_id = o.id
  and (o.transaction_id is null or o.confirmation_status = 'UNCONFIRMED');

-- Preserve active/conflict amounts across the upgrade.
insert into pm_amount_slot_reservation (
    id, merchant_id, platform, payable_amount_minor, order_id, status,
    reserved_at, created_at, updated_at
)
select
    o.id,
    o.merchant_id,
    o.platform,
    o.payable_amount_minor,
    o.id,
    'ACTIVE',
    o.created_at,
    o.created_at,
    o.updated_at
from pm_payment_order o
where o.status in ('PENDING', 'CONFLICT')
on conflict (merchant_id, platform, payable_amount_minor) do nothing;

-- Preserve the remaining cooling window for terminal orders completed shortly
-- before the upgrade. Active/conflict reservations above take precedence.
insert into pm_amount_slot_reservation (
    id, merchant_id, platform, payable_amount_minor, order_id, status,
    reserved_at, cooling_until, created_at, updated_at
)
select
    o.id,
    o.merchant_id,
    o.platform,
    o.payable_amount_minor,
    o.id,
    'COOLING',
    o.created_at,
    o.updated_at + interval '10 minutes',
    o.created_at,
    o.updated_at
from pm_payment_order o
where o.status in ('PAID', 'CANCELLED', 'EXPIRED')
  and o.updated_at + interval '10 minutes' > now()
on conflict (merchant_id, platform, payable_amount_minor) do nothing;

insert into sys_menu values
    (1900100000000000080, '支付交易', 1900100000000000001, 8, 'transaction', 'payment/transaction/index', '', 'N', 'Y', 'C', '0', '0', 'payment:transaction:list', 'list', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '支付观察、匹配与确认交易'),
    (1900100000000000090, '审批中心', 1900100000000000001, 9, 'approval', 'payment/approval/index', '', 'N', 'Y', 'C', '0', '0', 'payment:approval:list', 'audit', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '支付双人复核'),
    (1900100000000000100, '对账中心', 1900100000000000001, 10, 'reconciliation', 'payment/reconciliation/index', '', 'N', 'Y', 'C', '0', '0', 'payment:reconciliation:list', 'chart', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '版本化内部对账'),
    (1900100000000001080, '确认支付交易', 1900100000000000080, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:transaction:confirm', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001081, '撤销支付确认', 1900100000000000080, 2, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:transaction:reverse', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001090, '审批支付操作', 1900100000000000090, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:approval:review', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001100, '执行内部对账', 1900100000000000100, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:reconciliation:run', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001101, '处理对账差异', 1900100000000000100, 2, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:reconciliation:resolve', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001110, '管理主备设备', 1900100000000000020, 6, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:device:assignment', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '')
on conflict (menu_id) do nothing;

insert into sys_role_menu (role_id, menu_id)
select 1900200000000000001, menu_id
from sys_menu
where menu_id in (
    1900100000000000080,
    1900100000000000090,
    1900100000000000100,
    1900100000000001080,
    1900100000000001081,
    1900100000000001090,
    1900100000000001100,
    1900100000000001101,
    1900100000000001110
)
on conflict (role_id, menu_id) do nothing;
