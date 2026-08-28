-- Replace the development-only dual approval design with MFA-confirmed
-- single-user sensitive operations. Historical approval data is intentionally
-- discarded because the product is still in development.

alter table pm_reconciliation_item
    drop column if exists pending_approval_id cascade;

delete from pm_reconciliation_item
where status = 'PENDING_APPROVAL';

alter table pm_reconciliation_item
    drop constraint if exists chk_pm_reconciliation_item_status;

alter table pm_reconciliation_item
    add constraint chk_pm_reconciliation_item_status
        check (status in ('OPEN', 'RESOLVED', 'IGNORED'));

drop table if exists pm_payment_approval cascade;

create table pm_sensitive_operation_log
(
    id                  bigint primary key,
    merchant_id         bigint       not null references pm_merchant (id),
    operation_type      varchar(32)  not null,
    target_type         varchar(32)  not null,
    target_id           bigint       not null,
    reason              varchar(500),
    request_payload     jsonb        not null default '{}'::jsonb,
    before_snapshot     jsonb,
    after_snapshot      jsonb,
    operated_by         bigint       not null,
    operated_at         timestamptz  not null,
    verification_method varchar(16)  not null default 'MFA',
    idempotency_key     varchar(128) not null,
    constraint uk_pm_sensitive_operation_key
        unique (merchant_id, idempotency_key),
    constraint chk_pm_sensitive_operation_type
        check (operation_type in ('FORCE_MATCH', 'REVERSE_CONFIRMATION')),
    constraint chk_pm_sensitive_operation_verification
        check (verification_method = 'MFA')
);

create index idx_pm_sensitive_operation_merchant_time
    on pm_sensitive_operation_log (merchant_id, operated_at desc);

delete from sys_role_menu
where menu_id = 1900100000000001090;

delete from sys_menu
where menu_id = 1900100000000001090;

update sys_menu
set menu_name = '敏感操作记录',
    path = 'sensitive-operation',
    component = 'payment/sensitive-operation/index',
    perms = 'payment:sensitive-operation:list',
    icon = 'lock',
    remark = 'MFA 单人确认的强制补单和撤销记录',
    update_time = now()
where menu_id = 1900100000000000090;

update sys_menu
set remark = '支付交易、敏感操作记录和内部对账',
    update_time = now()
where menu_id = 1900100000000000180;
