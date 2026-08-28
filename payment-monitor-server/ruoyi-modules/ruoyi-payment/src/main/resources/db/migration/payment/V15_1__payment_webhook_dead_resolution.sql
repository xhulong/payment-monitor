alter table pm_webhook_outbox
    add column if not exists resolution_status varchar(16) not null default 'OPEN',
    add column if not exists resolved_by bigint,
    add column if not exists resolved_at timestamptz,
    add column if not exists resolution_note varchar(1000);

alter table pm_webhook_outbox
    drop constraint if exists chk_pm_webhook_outbox_resolution_status;

alter table pm_webhook_outbox
    add constraint chk_pm_webhook_outbox_resolution_status
        check (resolution_status in ('OPEN', 'RESOLVED', 'IGNORED'));

update pm_webhook_outbox
set resolution_status = case
    when status = 'DELIVERED' then 'RESOLVED'
    else 'OPEN'
end
where resolution_status is null
   or resolution_status not in ('OPEN', 'RESOLVED', 'IGNORED');

create index if not exists idx_pm_webhook_outbox_dead_resolution
    on pm_webhook_outbox (merchant_id, resolution_status, updated_at desc)
    where status = 'DEAD';
