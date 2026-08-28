alter table pm_payment_event
    add column if not exists event_time_ms bigint,
    add column if not exists client_received_at timestamptz,
    add column if not exists client_received_at_ms bigint,
    add column if not exists client_sent_at timestamptz,
    add column if not exists client_sent_at_ms bigint;

-- Preserve existing rows with the best timeline information available before V4.
update pm_payment_event
set event_time_ms = floor(extract(epoch from event_time) * 1000)::bigint
where event_time is not null
  and event_time_ms is null;

update pm_payment_event
set client_received_at = coalesce(event_time, received_at),
    client_received_at_ms = floor(
        extract(epoch from coalesce(event_time, received_at)) * 1000
    )::bigint,
    client_sent_at = received_at,
    client_sent_at_ms = floor(extract(epoch from received_at) * 1000)::bigint
where client_received_at is null
   or client_received_at_ms is null
   or client_sent_at is null
   or client_sent_at_ms is null;

alter table pm_payment_event
    alter column client_received_at set not null,
    alter column client_received_at_ms set not null,
    alter column client_sent_at set not null,
    alter column client_sent_at_ms set not null;

create index if not exists idx_pm_event_income_timeline
    on pm_payment_event (
        merchant_id,
        direction,
        event_time_ms desc,
        client_received_at_ms desc,
        received_at desc,
        id desc
    );
