-- Webhook payloads are unified on schema v2 during development.

with webhook_digests as (
    select id,
           decode(md5(
               merchant_id::text || ':' ||
               event_type || ':' ||
               aggregate_type || ':' ||
               aggregate_id::text
           ), 'hex') as digest
    from pm_webhook_outbox
),
normalized_digests as (
    select id,
           set_byte(
               set_byte(
                   digest,
                   6,
                   (get_byte(digest, 6) & 15) | 48
               ),
               8,
               (get_byte(digest, 8) & 63) | 128
           ) as digest
    from webhook_digests
),
stable_event_ids as (
    select id,
           substring(encode(digest, 'hex'), 1, 8) || '-' ||
           substring(encode(digest, 'hex'), 9, 4) || '-' ||
           substring(encode(digest, 'hex'), 13, 4) || '-' ||
           substring(encode(digest, 'hex'), 17, 4) || '-' ||
           substring(encode(digest, 'hex'), 21, 12) as event_id
    from normalized_digests
)
update pm_webhook_outbox outbox
set event_id = stable.event_id
from stable_event_ids stable
where outbox.id = stable.id;

update pm_webhook_outbox
set payload = payload || jsonb_build_object(
        'schemaVersion', 2,
        'eventId', event_id
    ),
    schema_version = 2;

alter table pm_webhook_outbox
    alter column schema_version set default 2;

alter table pm_webhook_outbox
    drop constraint if exists chk_pm_webhook_schema_version;

alter table pm_webhook_outbox
    add constraint chk_pm_webhook_schema_version
        check (schema_version = 2);

alter table pm_webhook_endpoint
    drop constraint if exists chk_pm_webhook_payload_version;

alter table pm_webhook_endpoint
    drop column if exists payload_version;
