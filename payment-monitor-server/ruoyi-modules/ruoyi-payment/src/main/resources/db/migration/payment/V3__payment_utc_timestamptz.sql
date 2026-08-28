-- V2 stored payment timestamps as Asia/Shanghai wall-clock values without a zone.
-- Convert them back to PostgreSQL timestamptz so the API and database share UTC semantics.

alter table pm_merchant
    alter column created_at type timestamptz using created_at at time zone 'Asia/Shanghai',
    alter column updated_at type timestamptz using updated_at at time zone 'Asia/Shanghai';

alter table pm_device
    alter column paired_at type timestamptz using paired_at at time zone 'Asia/Shanghai',
    alter column last_seen_at type timestamptz using last_seen_at at time zone 'Asia/Shanghai',
    alter column created_at type timestamptz using created_at at time zone 'Asia/Shanghai',
    alter column updated_at type timestamptz using updated_at at time zone 'Asia/Shanghai';

alter table pm_device_credential
    alter column created_at type timestamptz using created_at at time zone 'Asia/Shanghai',
    alter column revoked_at type timestamptz using revoked_at at time zone 'Asia/Shanghai';

alter table pm_pairing_code
    alter column expires_at type timestamptz using expires_at at time zone 'Asia/Shanghai',
    alter column used_at type timestamptz using used_at at time zone 'Asia/Shanghai',
    alter column created_at type timestamptz using created_at at time zone 'Asia/Shanghai';

alter table pm_payment_event
    alter column event_time type timestamptz using event_time at time zone 'Asia/Shanghai',
    alter column received_at type timestamptz using received_at at time zone 'Asia/Shanghai';
